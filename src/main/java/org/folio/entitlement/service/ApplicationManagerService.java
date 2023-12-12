package org.folio.entitlement.service;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.ClassUtils.getSimpleName;
import static org.folio.common.utils.PaginationUtils.loadInBatches;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.FeignException.BadRequest;
import feign.FeignException.NotFound;
import jakarta.persistence.EntityNotFoundException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.ListUtils;
import org.folio.common.domain.model.error.Error;
import org.folio.common.domain.model.error.ErrorResponse;
import org.folio.common.utils.CqlQuery;
import org.folio.entitlement.domain.model.ResultList;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.am.ApplicationManagerClient;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.am.model.ModuleDiscovery;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ApplicationManagerService {

  private static final int DEFAULT_NUMBER_OF_DISCOVERY_ENTITIES = 100;
  private static final int APP_DESCRIPTOR_BATCH_SIZE = 50;

  private final ApplicationManagerClient applicationManagerClient;
  private final ObjectMapper objectMapper;

  /**
   * Retrieves application descriptor by application id.
   *
   * @param applicationId - application descriptor identifier
   * @param token - auth token
   * @return found {@link ApplicationDescriptor} by application id
   * @throws EntityNotFoundException if {@link ApplicationDescriptor} is not found by id
   * @throws IntegrationException if other exception occurred during performing HTTP request
   */
  public ApplicationDescriptor getApplicationDescriptor(String applicationId, String token) {
    try {
      return applicationManagerClient.getApplicationDescriptor(applicationId, true, token);
    } catch (NotFound notFound) {
      throw new EntityNotFoundException("Application descriptor is not found: " + applicationId);
    } catch (FeignException cause) {
      throw new IntegrationException("Failed to retrieve application descriptor: " + applicationId, cause);
    }
  }

  /**
   * Queries application descriptors by application ids.
   *
   * @param applicationIds list of application ids
   * @param token auth token
   * @return @link ResultList} with {@link ApplicationDescriptor} descriptors
   * @throws IntegrationException if other exception occurred during performing HTTP request
   */
  public ResultList<ApplicationDescriptor> getApplicationDescriptors(List<String> applicationIds, Integer limit,
    Integer offset, String token) {
    try {
      var query = CqlQuery.exactMatchAny("id", applicationIds);
      return applicationManagerClient.queryApplicationDescriptors(query, true, limit, offset, token);
    } catch (FeignException cause) {
      throw new IntegrationException("Failed to query application descriptors", cause);
    }
  }

  /**
   * Queries application descriptors by ids in batches to prevent exceeding max query length.
   *
   * @param applicationIds application identifiers
   * @param token auth token
   * @return Application descriptors
   */
  public List<ApplicationDescriptor> getApplicationDescriptors(List<String> applicationIds, String token) {
    return loadInBatches(applicationIds, loadAppDescriptorsBatch(token), APP_DESCRIPTOR_BATCH_SIZE);
  }

  private Function<List<String>, List<ApplicationDescriptor>> loadAppDescriptorsBatch(String token) {
    return applicationIdsBatch ->
      getApplicationDescriptors(applicationIdsBatch, APP_DESCRIPTOR_BATCH_SIZE, 0, token)
        .getRecords();
  }

  /**
   * Retrieves application module discovery descriptors from mgr-applications service.
   *
   * @param applicationId - application id to retrieve module discoveries
   * @return {@link ResultList} with {@link ModuleDiscovery} descriptors
   * @throws IntegrationException if other exception occurred during performing HTTP request
   */
  public ResultList<ModuleDiscovery> getModuleDiscoveries(String applicationId, String token) {
    try {
      return applicationManagerClient.getModuleDiscoveries(applicationId, DEFAULT_NUMBER_OF_DISCOVERY_ENTITIES, token);
    } catch (FeignException cause) {
      throw new IntegrationException("Failed to retrieve module discovery descriptors: " + applicationId, cause);
    }
  }

  /**
   * Validates application descriptor.
   *
   * @param descriptor - application descriptor object
   * @param token - x-okapi-token
   * @throws RequestValidationException if application descriptor is not correct
   * @throws IntegrationException in other cases
   */
  public void validate(ApplicationDescriptor descriptor, String token) {
    try {
      applicationManagerClient.validate(descriptor, token);
    } catch (BadRequest badRequest) {
      var error = extractError(badRequest);

      throw new RequestValidationException("Invalid application descriptor. Details: "
        + (error != null ? error.getMessage() : badRequest.getMessage()),
        "application", descriptor.getId());
    } catch (FeignException cause) {
      throw new IntegrationException("Failed to validate application descriptor: " + descriptor.getId(), cause);
    }
  }

  private Error extractError(BadRequest badRequest) {
    return badRequest.responseBody()
      .map(this::parseErrors)
      .map(this::first)
      .orElse(null);
  }

  private List<Error> parseErrors(ByteBuffer response) {
    var responseStr = new String(response.array());

    try {
      var errorResponse = objectMapper.readValue(responseStr, ErrorResponse.class);

      return errorResponse.getErrors();
    } catch (JsonProcessingException e) {
      log.warn("Failed to parse {} from the response: {}", getSimpleName(ErrorResponse.class), responseStr);

      return emptyList();
    }
  }

  private <T> T first(List<T> items) {
    return ListUtils.emptyIfNull(items).size() == 1 ? items.get(0) : null;
  }
}
