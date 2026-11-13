package org.folio.entitlement.integration.folio;

import static java.lang.String.format;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static org.folio.common.utils.OkapiHeaders.TENANT;
import static org.folio.entitlement.utils.TenantApiUtils.DISABLE_TENANT_ENDPOINT;
import static org.folio.entitlement.utils.TenantApiUtils.TENANT_ENDPOINT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.LOCATION;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.folio.configuration.FolioClientConfigurationProperties;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.integration.folio.model.TenantAttributes;
import org.folio.entitlement.integration.folio.model.TenantJob;
import org.folio.entitlement.utils.JsonConverter;
import org.springframework.http.HttpMethod;

@Log4j2
@RequiredArgsConstructor
public class FolioTenantApiClient {

  public static final String POST = HttpMethod.POST.name();
  public static final String DELETE = HttpMethod.DELETE.name();

  private final HttpClient httpClient;
  private final JsonConverter jsonConverter;
  private final FolioClientConfigurationProperties clientConfiguration;

  /**
   * Sends a tenant installation request for module using provided module location.
   *
   * @param request - module request body with essential parameters
   */
  public void install(ModuleRequest request) {
    var httpResponse = sendHttpRequest(request,
      createInstallAttributes(request.getModuleId(), request.getTenantParameters()));
    if (httpResponse.statusCode() == CREATED.value()) {
      awaitTenantJob(request, httpResponse);
    }
  }

  /**
   * Sends a tenant uninstallation request for module using provided module location.
   *
   * @param request - module request body with essential parameters
   */
  public void uninstall(ModuleRequest request) {
    sendHttpRequest(request, createUninstallAttributes(request.getModuleId(), request.isPurge()));
  }

  /**
   * Sends a legacy tenant purge request for module using provided module location.
   *
   * @param request - module request body with essential parameters
   */
  public void uninstallLegacy(ModuleRequest request) {
    var tenantAttributes = createUninstallAttributes(request.getModuleId(), true);
    sendHttpRequest(request, tenantAttributes, DELETE, TENANT_ENDPOINT);
  }

  /**
   * Sends a legacy tenant disable request for module using provided module location.
   *
   * @param request - module request body with essential parameters
   */
  public void disableLegacy(ModuleRequest request) {
    var tenantAttributes = createUninstallAttributes(request.getModuleId(), false);
    sendHttpRequest(request, tenantAttributes, POST, DISABLE_TENANT_ENDPOINT);
  }

  private HttpResponse<String> sendHttpRequest(ModuleRequest request, TenantAttributes requestBody) {
    return sendHttpRequest(request, requestBody, POST, TENANT_ENDPOINT);
  }

  private HttpResponse<String> sendHttpRequest(ModuleRequest request, TenantAttributes requestBody,
    String httpMethod, String endpoint) {
    var jsonBody = jsonConverter.toJson(requestBody);
    var httpRequest = createHttpRequest(request, jsonBody, httpMethod, endpoint);
    var httpResponse = sendRequest(httpRequest);
    var statusCode = httpResponse.statusCode();
    if (statusCode > 300) {
      var responseBody = httpResponse.body();
      log.info("Module responded with error code: uri = {}, code = {}, responseBody = {},\n"
        + "moduleId = {}, applicationId = {}, tenant = {}",
        httpRequest.uri().getPath(), statusCode, responseBody,
        request.getModuleId(), request.getApplicationId(), request.getTenantName());

      throw new IntegrationException("Failed to perform doPostTenant call",
        List.of(new Parameter().key("cause").value(format("%s: %s", statusCode, responseBody))), statusCode);
    }

    if (statusCode != NO_CONTENT.value()) {
      log.debug("Module responded with code: {}, expected '[204] No Content' [moduleId='{}', url={}, tenant='{}']",
        statusCode, request.getModuleId(), request.getLocation(), request.getTenantName());
    }

    return httpResponse;
  }

  /**
   * Polls the async Tenant API job (RMB contract: 201 Created + job id) until it completes, fails or times out.
   */
  private void awaitTenantJob(ModuleRequest request, HttpResponse<String> createResponse) {
    var jobId = getJobId(createResponse);
    if (jobId == null) {
      log.warn("Module responded with 201 Created without a job id, skipping job status check [moduleId='{}',"
        + " tenant='{}']", request.getModuleId(), request.getTenantName());
      return;
    }

    var jobRequest = createJobRequest(request, jobId);
    var deadline = System.nanoTime() + clientConfiguration.getTenantApiJobTimeout().toNanos();
    var job = getTenantJob(jobRequest);
    while (!job.isComplete()) {
      if (System.nanoTime() > deadline) {
        throw new IntegrationException(format("Tenant job has not completed in %s [moduleId: %s, jobId: %s]",
          clientConfiguration.getTenantApiJobTimeout(), request.getModuleId(), jobId), List.of());
      }

      sleep(jobRequest);
      job = getTenantJob(jobRequest);
    }

    checkJobError(request, jobId, job);
  }

  private HttpRequest createJobRequest(ModuleRequest request, String jobId) {
    return HttpRequest.newBuilder()
      .uri(URI.create(request.getLocation() + TENANT_ENDPOINT + "/" + jobId))
      .GET()
      .header(TENANT, request.getTenantName())
      .timeout(clientConfiguration.getReadTimeout())
      .version(HTTP_1_1)
      .build();
  }

  private TenantJob getTenantJob(HttpRequest jobRequest) {
    var response = sendRequest(jobRequest);
    if (response.statusCode() != OK.value()) {
      throw new IntegrationException("Failed to get tenant job status",
        List.of(new Parameter().key("cause").value(format("%s: %s", response.statusCode(), response.body()))),
        response.statusCode());
    }

    return jsonConverter.fromJson(response.body(), TenantJob.class);
  }

  private void checkJobError(ModuleRequest request, String jobId, TenantJob job) {
    if (job.getError() != null && !job.getError().isBlank()) {
      log.info("Tenant job failed: moduleId = {}, applicationId = {}, tenant = {}, jobId = {}, error = {}",
        request.getModuleId(), request.getApplicationId(), request.getTenantName(), jobId, job.getError());
      throw new IntegrationException("Tenant job failed",
        List.of(new Parameter().key("cause").value(job.getError())));
    }
  }

  private String getJobId(HttpResponse<String> response) {
    var location = response.headers().firstValue(LOCATION).orElse(null);
    if (location != null && !location.isBlank()) {
      return location.substring(location.lastIndexOf('/') + 1);
    }

    var body = response.body();
    return body == null || body.isBlank() ? null : jsonConverter.fromJson(body, TenantJob.class).getId();
  }

  private void sleep(HttpRequest request) {
    try {
      Thread.sleep(clientConfiguration.getTenantApiJobPollInterval().toMillis());
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IntegrationException(format("Request has been interrupted [method: %s, uri: %s]",
        request.method(), request.uri()), error);
    }
  }

  private HttpRequest createHttpRequest(ModuleRequest request, String jsonBody, String httpMethod, String endpoint) {
    return HttpRequest.newBuilder()
      .uri(URI.create(request.getLocation() + endpoint))
      .method(httpMethod, BodyPublishers.ofString(jsonBody))
      .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
      .header(TENANT, request.getTenantName())
      .timeout(clientConfiguration.getReadTimeout())
      .version(HTTP_1_1)
      .build();
  }

  private HttpResponse<String> sendRequest(HttpRequest request) {
    try {
      return httpClient.send(request, BodyHandlers.ofString());
    } catch (IOException error) {
      throw new IntegrationException(format("[%s] Failed to perform request [method: %s, uri: %s]",
        error.getClass().getSimpleName(), request.method(), request.uri()), error);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IntegrationException(format("Request has been interrupted [method: %s, uri: %s]",
        request.method(), request.uri()), error);
    }
  }

  private static TenantAttributes createInstallAttributes(String moduleId, List<Parameter> parameters) {
    var tenantAttributes = new TenantAttributes();
    tenantAttributes.setModuleTo(moduleId);
    tenantAttributes.setParameters(parameters);
    return tenantAttributes;
  }

  private static TenantAttributes createUninstallAttributes(String moduleId, Boolean purge) {
    var tenantAttributes = new TenantAttributes();
    tenantAttributes.setPurge(purge);
    tenantAttributes.setModuleFrom(moduleId);
    return tenantAttributes;
  }
}
