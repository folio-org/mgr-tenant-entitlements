package org.folio.entitlement.integration.okapi.flow;

import static java.util.Objects.requireNonNull;
import static org.folio.entitlement.utils.EntitlementServiceUtils.toUnmodifiableMap;

import java.util.List;
import java.util.Map;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.service.flow.ModulesFlowProvider;
import org.folio.flow.api.Flow;
import org.folio.flow.api.StageContext;

public class OkapiModulesFlowProvider implements ModulesFlowProvider {

  private final Map<EntitlementType, OkapiModulesFlowFactory> okapiFlowFactories;

  /**
   * Creates {@link OkapiModulesFlowProvider}.
   *
   * @param flowFactories - list with {@link OkapiModulesFlowFactory} beans
   */
  public OkapiModulesFlowProvider(List<OkapiModulesFlowFactory> flowFactories) {
    this.okapiFlowFactories = toUnmodifiableMap(flowFactories, OkapiModulesFlowFactory::getEntitlementType);
  }

  @Override
  public Flow createFlow(StageContext context) {
    var ctx = ApplicationStageContext.decorate(context);
    var request = ctx.getEntitlementRequest();
    return requireNonNull(okapiFlowFactories.get(request.getType())).createFlow(ctx);
  }
}
