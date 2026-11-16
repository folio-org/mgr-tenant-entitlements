package org.folio.entitlement.integration.folio;

import static java.lang.String.format;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static org.folio.common.utils.OkapiHeaders.TENANT;
import static org.folio.entitlement.utils.TenantApiUtils.DISABLE_TENANT_ENDPOINT;
import static org.folio.entitlement.utils.TenantApiUtils.TENANT_ENDPOINT;
import static org.springframework.http.HttpHeaders.ACCEPT;
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
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.StringUtils;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.folio.configuration.FolioClientConfigurationProperties;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.integration.folio.model.TenantAttributes;
import org.folio.entitlement.integration.folio.model.TenantJob;
import org.folio.entitlement.integration.folio.model.TenantOperation;
import org.folio.entitlement.utils.JsonConverter;
import org.springframework.http.HttpMethod;

/**
 * Calls the tenant API of FOLIO modules: installs and uninstalls a module for a tenant and reads tenant jobs.
 *
 * <p>Whether a module finished a call itself or accepted it as a background job is decided from the response: a
 * {@code 201} with a tenant job is an accepted operation, any other success is a completed one.</p>
 */
@Log4j2
@RequiredArgsConstructor
public class FolioTenantApiClient {

  public static final String POST = HttpMethod.POST.name();
  public static final String DELETE = HttpMethod.DELETE.name();

  private static final String JOB_ID_REGEX = "[A-Za-z0-9-]{1,64}";
  private static final Pattern JOB_ID_PATTERN = Pattern.compile(JOB_ID_REGEX);
  private static final Pattern QUERY_OR_FRAGMENT_PATTERN = Pattern.compile("[?#]");
  private static final Pattern LOCATION_PATTERN = Pattern.compile(".*/_/tenant/(" + JOB_ID_REGEX + ")/?");

  private final HttpClient httpClient;
  private final JsonConverter jsonConverter;
  private final FolioClientConfigurationProperties clientConfiguration;

  /**
   * Sends a tenant installation request for module using provided module location.
   *
   * @param request - module request body with essential parameters
   * @return {@link TenantOperation#completed()} when the module did the work in the call, or an accepted operation
   *   with the tenant job id when the module continues in the background
   */
  public TenantOperation install(ModuleRequest request) {
    var attributes = createInstallAttributes(request.getModuleId(), request.getTenantParameters());
    return toTenantOperation(request, sendHttpRequest(request, attributes));
  }

  /**
   * Reads a tenant job, waiting on the module side until the job changes or the wait time elapses.
   *
   * @param request - module request body with essential parameters
   * @param jobId - tenant job identifier returned by the module
   * @param wait - maximum time the module may hold the request while the job is not complete
   * @return current state of the tenant job
   */
  public TenantJob getTenantJob(ModuleRequest request, String jobId, Duration wait) {
    var uri = URI.create(request.getLocation() + TENANT_ENDPOINT + "/" + jobId + "?wait=" + wait.toMillis());
    var httpRequest = HttpRequest.newBuilder()
      .uri(uri)
      .GET()
      .header(ACCEPT, APPLICATION_JSON_VALUE)
      .header(TENANT, request.getTenantName())
      .timeout(clientConfiguration.getReadTimeout().plus(wait))
      .version(HTTP_1_1)
      .build();

    var response = sendRequest(httpRequest);
    if (response.statusCode() != OK.value()) {
      var cause = new Parameter().key("cause").value(format("%s: %s", response.statusCode(), response.body()));
      throw new IntegrationException("Failed to get tenant job", List.of(cause), response.statusCode());
    }

    return jsonConverter.fromJson(response.body(), TenantJob.class);
  }

  /**
   * Sends a tenant uninstallation request for module using provided module location.
   *
   * @param request - module request body with essential parameters
   * @return {@link TenantOperation#completed()} when the module did the work in the call (the usual answer for
   *   purge), or an accepted operation with the tenant job id when the module continues in the background
   */
  public TenantOperation uninstall(ModuleRequest request) {
    var attributes = createUninstallAttributes(request.getModuleId(), request.isPurge());
    return toTenantOperation(request, sendHttpRequest(request, attributes));
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
   * @return {@link TenantOperation#completed()} or an accepted operation, as for {@link #uninstall(ModuleRequest)}
   */
  public TenantOperation disableLegacy(ModuleRequest request) {
    var tenantAttributes = createUninstallAttributes(request.getModuleId(), false);
    return toTenantOperation(request, sendHttpRequest(request, tenantAttributes, POST, DISABLE_TENANT_ENDPOINT));
  }

  private HttpResponse<String> sendHttpRequest(ModuleRequest request, TenantAttributes requestBody) {
    return sendHttpRequest(request, requestBody, POST, TENANT_ENDPOINT);
  }

  /**
   * Sends a tenant API request and checks its status.
   *
   * @param request - module request body with essential parameters
   * @param requestBody - tenant attributes to send
   * @param httpMethod - HTTP method of the call
   * @param endpoint - tenant API endpoint, relative to the module location
   * @return the response of a successful call
   * @throws IntegrationException if the module answers with an error status or the call fails
   */
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
   * Decides from the response whether the module finished the call or accepted it as a background job.
   *
   * @param request - module request the call was made for
   * @param response - successful response of the call
   * @return accepted operation for a {@code 201} with a usable job id, completed operation otherwise
   */
  private TenantOperation toTenantOperation(ModuleRequest request, HttpResponse<String> response) {
    if (response.statusCode() != CREATED.value()) {
      return TenantOperation.completed();
    }

    var jobId = extractJobId(response);
    if (jobId == null) {
      log.warn("Module responded with '[201] Created' without a tenant job, treating the operation as completed "
        + "[moduleId='{}', url={}, tenant='{}']", request.getModuleId(), request.getLocation(),
        request.getTenantName());
      return TenantOperation.completed();
    }

    log.debug("Module accepted the tenant operation as a job [moduleId='{}', jobId={}, tenant='{}']",
      request.getModuleId(), jobId, request.getTenantName());
    return TenantOperation.accepted(jobId);
  }

  /**
   * Reads the tenant job id from the {@code Location} header, or from the body when there is no header.
   *
   * <p>The id is placed into a URL path later, so only a {@code Location} path ending with {@code /_/tenant/{id}} and
   * an id of letters, digits and dashes are accepted.</p>
   *
   * @param response - response with status {@code 201}
   * @return job id, or {@code null} if the response carries no usable one
   */
  private String extractJobId(HttpResponse<String> response) {
    var location = response.headers().firstValue(LOCATION);
    if (location.isPresent()) {
      var matcher = LOCATION_PATTERN.matcher(QUERY_OR_FRAGMENT_PATTERN.split(location.get(), 2)[0]);
      return matcher.matches() ? matcher.group(1) : null;
    }

    var bodyId = jobIdFromBody(response.body());
    return bodyId != null && JOB_ID_PATTERN.matcher(bodyId).matches() ? bodyId : null;
  }

  /**
   * Reads the job id from a response body that holds a tenant job.
   *
   * @param body - response body, may be blank
   * @return job id, or {@code null} if the body is blank, is not a tenant job or has no id
   */
  private String jobIdFromBody(String body) {
    if (StringUtils.isBlank(body)) {
      return null;
    }

    try {
      var job = jsonConverter.fromJson(body, TenantJob.class);
      return job == null ? null : job.getId();
    } catch (SerializationException e) {
      log.debug("Failed to read tenant job from the response body: {}", e.getMessage());
      return null;
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
