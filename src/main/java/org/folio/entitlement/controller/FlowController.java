package org.folio.entitlement.controller;

import static java.lang.Boolean.TRUE;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.ApplicationFlows;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.entitlement.domain.dto.FlowStages;
import org.folio.entitlement.domain.dto.Flows;
import org.folio.entitlement.rest.resource.FlowApi;
import org.folio.entitlement.service.FlowStageService;
import org.folio.entitlement.service.flow.ApplicationFlowService;
import org.folio.entitlement.service.flow.FlowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FlowController extends BaseController implements FlowApi {

  private final FlowService flowService;
  private final ApplicationFlowService applicationFlowService;
  private final FlowStageService flowStageService;

  @Override
  public ResponseEntity<Flow> getFlowById(UUID flowId, Boolean includeStages) {
    return ResponseEntity.ok(flowService.getById(flowId, TRUE.equals(includeStages)));
  }

  @Override
  public ResponseEntity<Flows> findFlows(String query, Integer limit, Integer offset) {
    var flowSearchResult = flowService.find(query, limit, offset);
    return ResponseEntity.ok(new Flows()
      .flows(flowSearchResult.getRecords())
      .totalRecords(flowSearchResult.getTotalRecords()));
  }

  @Override
  public ResponseEntity<ApplicationFlows> findApplicationFlows(String query, Integer limit, Integer offset) {
    var searchResult = applicationFlowService.find(query, limit, offset);
    return ResponseEntity.ok(new ApplicationFlows()
      .totalRecords(searchResult.getTotalRecords())
      .applicationFlows(searchResult.getRecords()));
  }

  @Override
  public ResponseEntity<ApplicationFlow> getApplicationFlowById(UUID applicationFlowId, Boolean includeStages) {
    var flow = applicationFlowService.getById(applicationFlowId, TRUE.equals(includeStages));
    return ResponseEntity.ok(flow);
  }

  @Override
  public ResponseEntity<FlowStage> getApplicationFlowStageByName(UUID applicationFlowId, String stageName) {
    return ResponseEntity.ok(flowStageService.getEntitlementStage(applicationFlowId, stageName));
  }

  @Override
  public ResponseEntity<FlowStages> getApplicationFlowStages(UUID applicationFlowId) {
    var entitlementStages = flowStageService.findByFlowId(applicationFlowId);
    return ResponseEntity.ok(new FlowStages()
      .totalRecords(entitlementStages.getTotalRecords())
      .stages(entitlementStages.getRecords()));
  }
}
