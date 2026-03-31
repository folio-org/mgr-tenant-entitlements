package org.folio.entitlement.service;

import static java.lang.String.join;
import static java.util.Collections.emptyList;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.ClassUtils.getSimpleName;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.common.utils.PaginationUtils.loadInBatches;

import jakarta.persistence.EntityNotFoundException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.common.domain.model.error.Error;
import org.folio.common.domain.model.error.ErrorResponse;
import org.folio.common.utils.CqlQuery;
import org.folio.entitlement.domain.model.ResultList;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.am.ApplicationManagerClient;
import org.folio.entitlement.integration.am.model.ModuleDiscovery;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

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
    } catch (HttpClientErrorException.NotFound notFound) {
      throw new EntityNotFoundException("Application descriptor is not found: " + applicationId);
    } catch (RestClientResponseException cause) {
      throw new IntegrationException("Failed to retrieve application descriptor: " + applicationId, cause);
    }
  }

  /**
   * Queries application descriptors by ids in batches to prevent exceeding max query length.
   *
   * @param applicationIds application identifiers
   * @param token auth token
   * @return Application descriptors
   */
  public List<ApplicationDescriptor> getApplicationDescriptors(Collection<String> applicationIds, String token) {
    var sortedIds = toStream(applicationIds).sorted().toList();
    var descriptors = loadInBatches(sortedIds, loadAppDescriptorsBatch(token), APP_DESCRIPTOR_BATCH_SIZE);
    checkAllApplicationsFound(applicationIds, descriptors);
    return descriptors;
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
    } catch (RestClientResponseException cause) {
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
    } catch (HttpClientErrorException.BadRequest badRequest) {
      var error = extractError(badRequest);

      throw new RequestValidationException("Invalid application descriptor. Details: "
        + (error != null ? error.getMessage() : badRequest.getMessage()),
        "application", descriptor.getId());
    } catch (RestClientResponseException cause) {
      throw new IntegrationException("Failed to validate application descriptor: " + descriptor.getId(), cause);
    }
  }

  private Function<List<String>, List<ApplicationDescriptor>> loadAppDescriptorsBatch(String token) {
    return applicationIdsBatch ->
      loadApplicationDescriptors(applicationIdsBatch, token).getRecords();
  }

  private Error extractError(HttpClientErrorException.BadRequest badRequest) {
    var responseBody = badRequest.getResponseBodyAsByteArray();
    if (responseBody.length == 0) {
      return null;
    }
    return parseErrors(badRequest.getResponseBodyAsString())
      .stream()
      .findFirst()
      .orElse(null);
  }

  /**
   * Queries application descriptors by application ids.
   *
   * @param applicationIds list of application ids
   * @param token auth token
   * @return @link ResultList} with {@link ApplicationDescriptor} descriptors
   * @throws IntegrationException if other exception occurred during performing HTTP request
   */
  private ResultList<ApplicationDescriptor> loadApplicationDescriptors(List<String> applicationIds, String token) {
    try {
      var query = CqlQuery.exactMatchAny("id", applicationIds);
      return applicationManagerClient.queryApplicationDescriptors(query, true, APP_DESCRIPTOR_BATCH_SIZE, 0, token);
    } catch (RestClientResponseException cause) {
      throw new IntegrationException("Failed to query application descriptors", cause);
    }
  }

  private List<Error> parseErrors(String responseStr) {
    try {
      var errorResponse = objectMapper.readValue(responseStr, ErrorResponse.class);

      return errorResponse.getErrors();
    } catch (JacksonException e) {
      log.warn("Failed to parse {} from the response: {}", getSimpleName(ErrorResponse.class), responseStr);

      return emptyList();
    }
  }

  private static void checkAllApplicationsFound(Collection<String> appIds, List<ApplicationDescriptor> descriptors) {
    var notFoundIds = new HashSet<>(appIds);

    descriptors.forEach(descriptor -> notFoundIds.remove(descriptor.getId()));

    if (isNotEmpty(notFoundIds)) {
      throw new RequestValidationException(
        "Applications not found by the given ids", "applicationIds", join(", ", notFoundIds));
    }
  }
}
