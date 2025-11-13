package org.folio.entitlement.service.flow;

import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.flow.api.Flow;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;

@RequiredArgsConstructor
public abstract class AbstractApplicationsFlowProvider {

  protected final LayerFlowProvider layerFlowProvider;

  public Flow createFlow(StageContext stageContext) {
    var ctx = CommonStageContext.decorate(stageContext);
    var flowId = ctx.flowId();
    var request = ctx.getEntitlementRequest();

    var appFlows = createApplicationFlows(ctx);

    ctx.clearContext();

    return buildApplicationsFlow(flowId, request, appFlows);
  }

  public String getName() {
    return this.getClass().getSimpleName();
  }

  protected abstract List<? extends Stage<? extends StageContext>> createApplicationFlows(CommonStageContext ctx);

  private static Flow buildApplicationsFlow(String flowId, EntitlementRequest request,
    List<? extends Stage<? extends StageContext>> stages) {
    var builder = Flow.builder()
      .id(flowId + "/ApplicationsFlow")
      .executionStrategy(request.getExecutionStrategy())
      .flowParameter(PARAM_REQUEST, request);

    stages.forEach(builder::stage);

    return builder.build();
  }
}
