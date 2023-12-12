package org.folio.entitlement.service;

import static java.util.concurrent.CompletableFuture.failedFuture;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.dto.ExtendedEntitlements;
import org.folio.entitlement.domain.model.EntitlementFlowHolder;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.ResultList;
import org.folio.entitlement.service.flow.EntitlementFlowService;
import org.folio.flow.api.Flow;
import org.folio.flow.api.FlowEngine;
import org.folio.flow.exception.StageExecutionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

@Log4j2
@Service
@RequiredArgsConstructor
public class EntitlementService {

  private final FlowEngine flowEngine;
  private final EntitlementFlowService entitlementFlowService;
  private final EntitlementCrudService entitlementCrudService;
  private final EntitlementValidationService entitlementValidationService;
  private final TransactionOperations trxOperations;

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
   * Entitles / revokes application entitlement from tenants.
   *
   * @param request - an entitlement request with required parameter to entitle tenant with applications.
   * @return a {@link ResultList} object with {@link EntitlementRequest} values
   */
  public ExtendedEntitlements execute(EntitlementRequest request) {
    entitlementValidationService.validate(request);

    var flowId = UUID.randomUUID();
    var applicationFlows = entitlementFlowService.createApplicationFlowsWithStatusQueued(flowId, request);
    var flowHandler = entitlementFlowService.createEntitlementFlow(flowId, request, applicationFlows);

    executeFlow(request, flowHandler);
    return flowHandler.getEntitlements();
  }

  private void executeFlow(EntitlementRequest request, EntitlementFlowHolder flowHandler) {
    var flow = flowHandler.getFlow();

    if (request.isAsync()) {
      executeFlowAsync(flow);
    } else {
      executeFlowSync(flow);
    }
  }

  private void executeFlowAsync(Flow flow) {
    var f = (CompletableFuture<Void>) flowEngine.executeAsync(flow);

    f.exceptionallyCompose(throwable -> {
      trxOperations.executeWithoutResult(transactionStatus -> deleteQueuedApplicationFlows(flow));
      return failedFuture(throwable);
    });
  }

  private void executeFlowSync(Flow flow) {
    try {
      flowEngine.execute(flow);
    } catch (StageExecutionException e) {
      trxOperations.executeWithoutResult(transactionStatus -> deleteQueuedApplicationFlows(flow));
      throw e;
    }
  }

  private void deleteQueuedApplicationFlows(Flow flow) {
    var flowEntity = entitlementFlowService.findById(UUID.fromString(flow.getId()), false);

    flowEntity.getApplicationFlows().stream()
      .filter(inQueuedStatus())
      .forEach(appFlow -> entitlementFlowService.deleteApplicationFlow(appFlow.getId()));
  }

  private static Predicate<ApplicationFlow> inQueuedStatus() {
    return appFlow -> ExecutionStatus.QUEUED.equals(appFlow.getStatus());
  }
}
