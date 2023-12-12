package org.folio.entitlement.integration.tm;

import feign.FeignException;
import feign.FeignException.NotFound;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.tm.model.Tenant;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class TenantManagerService {

  private final TenantManagerClient tenantManagerClient;

  /**
   * Retrieves tenant by tenant id.
   *
   * @param tenantId - tenant id as {@link UUID}
   * @param authToken - authorization x-okapi-token as {@link String}
   * @return found {@link Tenant} object
   */
  public Tenant findTenant(UUID tenantId, String authToken) {
    log.info("Retrieving tenant [tenantId: {}]", tenantId);
    try {
      return tenantManagerClient.getTenantById(tenantId, authToken);
    } catch (NotFound notFound) {
      throw new EntityNotFoundException("Tenant is not found: " + tenantId);
    } catch (FeignException cause) {
      throw new IntegrationException("Failed to retrieve tenant: " + tenantId, cause);
    }
  }

  /**
   * Retrieves tenant by tenant id.
   *
   * @param tenantName - tenant name as {@link String}
   * @param authToken - authorization x-okapi-token as {@link String}
   * @return found {@link Tenant} object
   */
  public Tenant findTenantByName(String tenantName, String authToken) {
    log.info("Querying tenant [tenantName: {}]", tenantName);
    try {
      var result = tenantManagerClient.queryTenantsByName(tenantName, authToken);
      if (result == null || result.isEmpty()) {
        throw new EntityNotFoundException("Tenant is not found by name: " + tenantName);
      }
      if (result.getRecords().size() > 1) {
        throw new EntityNotFoundException("Multiple tenants found by name: " + tenantName);
      }
      return result.getRecords().get(0);
    } catch (FeignException cause) {
      throw new IntegrationException("Failed to query tenant by name: " + tenantName, cause);
    }
  }
}
