package org.folio.entitlement.service;

import static java.util.Collections.singletonList;
import static org.folio.common.utils.CollectionUtils.mapItems;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.dto.ExtendedEntitlements;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.ResultList;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.flow.FlowProvider;
import org.folio.flow.api.Flow;
import org.folio.flow.api.FlowEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class EntitlementService {

  private final FlowEngine flowEngine;
  private final FlowProvider flowProvider;
  private final EntitlementCrudService entitlementCrudService;

  /**
   * Find entitlements by tenant name filter or CQL query.
   *
   * @param query - CQL query string
   * @param tenant - tenant name filter
   * @param includeModules - flag to include modules
   * @param limit - max number of results
   * @param offset - offset for pagination
   * @return ResultList of Entitlements
   */
  @Transactional(readOnly = true)
  public ResultList<Entitlement> findByQueryOrTenant(String query, String tenant,
    Boolean includeModules, Integer limit, Integer offset) {
    if (query != null && tenant != null) {
      throw new RequestValidationException("Cannot use both 'query' and 'tenant' parameters together",
        singletonList(new Parameter().key("parameter").value("query,tenant")));
    }

    if (tenant != null) {
      log.debug("Receiving entitlements by tenant [tenant='{}', limit={}, offset={}]", tenant, limit, offset);
      var cqlQuery = String.format("tenant==%s", tenant);
      return entitlementCrudService.findByQuery(cqlQuery, includeModules, limit, offset);
    }

    log.debug("Receiving entitlements by query [cqlQuery='{}', limit={}, offset={}]", query, limit, offset);
    return entitlementCrudService.findByQuery(query, includeModules, limit, offset);
  }

  /**
   * Retrieves all applications installed for the specified tenant.
   *
   * @param cqlQuery - the specific tenant
   * @param includeModules include modules
   * @return a {@link ResultList} object with {@link EntitlementRequest} values
   */
  @Transactional(readOnly = true)
  public ResultList<Entitlement> findByQuery(String cqlQuery, Boolean includeModules, Integer limit, Integer offset) {
    log.debug("Receiving entitlements by query [cqlQuery='{}', limit={}, offset={}]", cqlQuery, limit, offset);
    return entitlementCrudService.findByQuery(cqlQuery, includeModules, limit, offset);
  }

  /**
   * Performs enable, disable, or upgrade operation for applications in entitlement request for tenant.
   *
   * @param request - an entitlement request with required parameter to entitle tenant with applications.
   * @return a {@link ResultList} object with {@link EntitlementRequest} values
   */
  public ExtendedEntitlements performRequest(EntitlementRequest request) {
    var flow = flowProvider.createFlow(request);
    executeFlow(request, flow);
    var entitlements = mapItems(request.getApplications(), appId -> buildEntitlement(request.getTenantId(), appId));
    return buildEntitlements(entitlements, UUID.fromString(flow.getId()));
  }

  private void executeFlow(EntitlementRequest request, Flow flow) {
    if (request.isAsync()) {
      flowEngine.executeAsync(flow);
      return;
    }

    flowEngine.execute(flow);
  }

  private static Entitlement buildEntitlement(UUID tenantId, String appId) {
    return new Entitlement().tenantId(tenantId).applicationId(appId);
  }

  private static ExtendedEntitlements buildEntitlements(List<Entitlement> entitlements, UUID flowId) {
    return new ExtendedEntitlements()
      .entitlements(entitlements)
      .totalRecords(entitlements.size())
      .flowId(flowId);
  }
}
