package org.folio.entitlement.service.flow;

import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;

import java.util.List;
import org.folio.entitlement.domain.model.ApplicationEntitlement;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;
import org.springframework.stereotype.Component;

@Component
public class BaseApplicationsFlowProvider extends AbstractApplicationsFlowProvider {

  public BaseApplicationsFlowProvider(LayerFlowProvider layerFlowProvider) {
    super(layerFlowProvider);
  }

  @Override
  protected List<? extends Stage<? extends StageContext>> createApplicationFlows(CommonStageContext ctx) {
    var request = ctx.getEntitlementRequest();
    var entitlementType = request.getType();

    var descriptors = ctx.getApplicationDescriptors();
    var appEntitlements = mapItems(descriptors, d -> new ApplicationEntitlement(entitlementType, d));

    return entitlementType != REVOKE
      ? layerFlowProvider.prepareLayeredFlows(ctx, appEntitlements)
      : layerFlowProvider.prepareLayeredFlowsReversed(ctx, appEntitlements);
  }
}
