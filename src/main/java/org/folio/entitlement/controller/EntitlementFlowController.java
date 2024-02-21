package org.folio.entitlement.controller;

import static java.lang.Boolean.TRUE;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.ApplicationFlows;
import org.folio.entitlement.domain.dto.EntitlementFlow;
import org.folio.entitlement.domain.dto.EntitlementStage;
import org.folio.entitlement.domain.dto.EntitlementStages;
import org.folio.entitlement.rest.resource.EntitlementFlowApi;
import org.folio.entitlement.service.EntitlementStageService;
import org.folio.entitlement.service.flow.EntitlementFlowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EntitlementFlowController extends BaseController implements EntitlementFlowApi {

  private final EntitlementFlowService entitlementFlowService;
  private final EntitlementStageService entitlementStageService;

  @Override
  public ResponseEntity<ApplicationFlows> findApplicationFlows(String query, Integer limit, Integer offset) {
    var searchResult = entitlementFlowService.find(query, limit, offset);
    return ResponseEntity.ok(new ApplicationFlows()
      .totalRecords(searchResult.getTotalRecords())
      .applicationFlows(searchResult.getRecords()));
  }

  @Override
  public ResponseEntity<EntitlementFlow> getEntitlementFlowById(UUID flowId, Boolean includeStages) {
    return ResponseEntity.ok(entitlementFlowService.findById(flowId, TRUE.equals(includeStages)));
  }

  @Override
  public ResponseEntity<ApplicationFlow> getApplicationFlowById(UUID applicationFlowId, Boolean includeStages) {
    var flow = entitlementFlowService.findByApplicationFlowId(applicationFlowId, TRUE.equals(includeStages));
    return ResponseEntity.ok(flow);
  }

  @Override
  public ResponseEntity<EntitlementStages> findEntitlementStages(UUID applicationFlowId) {
    var entitlementStages = entitlementStageService.findEntitlementStages(applicationFlowId);
    return ResponseEntity.ok(new EntitlementStages()
      .totalRecords(entitlementStages.getTotalRecords())
      .entitlementStages(entitlementStages.getRecords()));
  }

  @Override
  public ResponseEntity<EntitlementStage> getEntitlementStageByName(UUID applicationFlowId, String name) {
    return ResponseEntity.ok(entitlementStageService.getEntitlementStage(applicationFlowId, name));
  }
}
