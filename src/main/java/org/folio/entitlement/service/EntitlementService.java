package org.folio.entitlement.service;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.common.utils.CollectionUtils.mapItems;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.configuration.FlowEngineConfigurationProperties;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.dto.ExtendedEntitlements;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.ResultList;
import org.folio.entitlement.exception.FlowExecutionTimeoutException;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.exception.RequestValidationException.Params;
import org.folio.entitlement.integration.tm.TenantManagerService;
import org.folio.entitlement.service.flow.FlowProvider;
import org.folio.entitlement.service.flow.FlowService;
import org.folio.flow.api.Flow;
import org.folio.flow.api.FlowEngine;
import org.folio.flow.exception.FlowExecutionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class EntitlementService {

  private final FlowEngine flowEngine;
  private final FlowProvider flowProvider;
  private final FlowService flowService;
  private final TenantManagerService tenantManagerService;
  private final EntitlementCrudService entitlementCrudService;
  private final FlowEngineConfigurationProperties flowEngineProperties;

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

  @Transactional(readOnly = true)
  public ResultList<Entitlement> findByQueryOrTenantName(String query, String tenant, Boolean includeModules,
    Integer limit, Integer offset, String token) {
    if (isNotBlank(query) && isNotBlank(tenant)) {
      throw new RequestValidationException("Both 'query' and 'tenant' parameters are provided "
        + "but only one of them has to be specified", new Params().add("query", query).add("tenant", tenant));
    }

    var finalQuery = query;
    if (isNotBlank(tenant)) {
      var tenantId = tenantManagerService.findTenantByName(tenant, token).getId();
      finalQuery = "tenantId==" + tenantId;
    }
    return findByQuery(finalQuery, includeModules, limit, offset);
  }
  
  /**
   * Performs enable, disable, or upgrade operation for applications in entitlement request for tenant.
   *
   * @param request - an entitlement request with required parameter to entitle tenant with applications.
   * @return a {@link ResultList} object with {@link EntitlementRequest} values
   */
  public ExtendedEntitlements performRequest(EntitlementRequest request) {
    log.info("Performing entitlement request: type = {}, tenantId = {}, applications = {}",
      request.getType(), request.getTenantId(), request.getApplications());

    var flow = flowProvider.createFlow(request);
    executeFlow(request, flow);
    var entitlements = mapItems(request.getApplications(), appId -> buildEntitlement(request.getTenantId(), appId));
    return buildEntitlements(entitlements, UUID.fromString(flow.getId()));
  }

  private void executeFlow(EntitlementRequest request, Flow flow) {
    if (request.isAsync()) {
      flowEngine.executeAsync(flow);
    } else {
      executeSyncFlow(flow);
    }
  }

  /**
   * Executes the flow and reports a flow that did not finish within the execution timeout as failed.
   *
   * <p>The flow engine swallows an interrupt and returns normally, so the interrupt flag is checked afterwards -
   * otherwise an incomplete flow would be reported as a successful request.</p>
   */
  private void executeSyncFlow(Flow flow) {
    try {
      flowEngine.execute(flow);
    } catch (FlowExecutionException exception) {
      if (exception.getCause() instanceof TimeoutException) {
        throw failFlowExecution(flow, exception.getCause());
      }
      throw exception;
    }

    if (Thread.currentThread().isInterrupted()) {
      throw failFlowExecution(flow, new InterruptedException("Flow execution has been interrupted"));
    }
  }

  private FlowExecutionTimeoutException failFlowExecution(Flow flow, Throwable cause) {
    var flowId = UUID.fromString(flow.getId());
    flowService.failIfNotTerminal(flowId);
    return new FlowExecutionTimeoutException(flowId, flowEngineProperties.getExecutionTimeout(), cause);
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
