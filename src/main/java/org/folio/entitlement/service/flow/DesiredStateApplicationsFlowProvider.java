package org.folio.entitlement.service.flow;

import static org.apache.commons.collections4.ListUtils.union;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;

import java.util.List;
import org.folio.entitlement.domain.model.ApplicationEntitlement;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;
import org.springframework.stereotype.Component;

@Component
public class DesiredStateApplicationsFlowProvider extends AbstractApplicationsFlowProvider {

  public DesiredStateApplicationsFlowProvider(LayerFlowProvider layerFlowProvider) {
    super(layerFlowProvider);
  }

  @Override
  protected List<? extends Stage<? extends StageContext>> createApplicationFlows(CommonStageContext ctx) {
    var descriptorsByType = ctx.getApplicationStateTransitionDescriptors();

    var revokeApps = mapItems(descriptorsByType.get(REVOKE), ApplicationEntitlement::revoke);
    var revokeFlows = layerFlowProvider.prepareLayeredFlowsReversed(ctx, revokeApps);

    var entitleApps = mapItems(descriptorsByType.get(ENTITLE), ApplicationEntitlement::entitle);
    var upgradeApps = mapItems(descriptorsByType.get(UPGRADE), ApplicationEntitlement::upgrade);
    var combinedApps = union(entitleApps, upgradeApps);
    var combinedFlows = layerFlowProvider.prepareLayeredFlows(ctx, combinedApps);

    return union(revokeFlows, combinedFlows);
  }
}
