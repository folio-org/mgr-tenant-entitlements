package org.folio.entitlement.integration.tm;

import static org.folio.common.utils.OkapiHeaders.TOKEN;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.UUID;
import org.folio.entitlement.domain.model.ResultList;
import org.folio.entitlement.integration.tm.model.Tenant;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(accept = APPLICATION_JSON_VALUE)
public interface TenantManagerClient {

  /**
   * Retrieves tenant by id from mgr-tenants.
   *
   * @param id - tenant identifier
   * @param token - optional x-okapi-token header value for authorization in Okapi
   * @return found {@link Tenant} object
   */
  @GetExchange("/tenants/{id}")
  Tenant getTenantById(@PathVariable UUID id, @RequestHeader(TOKEN) String token);

  /**
   * Queries tenant by name from mgr-tenants.
   *
   * @param tenantName - tenant name
   * @param token - optional x-okapi-token header value for authorization in Okapi
   * @return found {@link Tenant} object
   */
  @GetExchange("/tenants?query=name=={tenantName}")
  ResultList<Tenant> queryTenantsByName(@PathVariable String tenantName, @RequestHeader(TOKEN) String token);
}
