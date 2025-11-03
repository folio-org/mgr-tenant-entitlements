package org.folio.entitlement.service.flow;

import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.STATE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.UPGRADE;

import java.util.Map;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.flow.api.Flow;
import org.springframework.stereotype.Component;

@Component
public class FlowProvider {

  private final Map<EntitlementRequestType, FlowFactory> flowFactories;

  /**
   * Creates {@link FlowProvider} for provided factories.
   *
   * @param entitleFlowFactory - {@link EntitleFlowFactory} bean from spring context.
   * @param upgradeFlowFactory - {@link UpgradeFlowFactory} bean from spring context.
   * @param revokeFlowFactory  - {@link RevokeFlowFactory} bean from spring context.
   * @param stateFlowFactory - {@link DesiredStateFlowFactory} bean from spring context.
   */
  public FlowProvider(EntitleFlowFactory entitleFlowFactory, UpgradeFlowFactory upgradeFlowFactory,
    RevokeFlowFactory revokeFlowFactory, DesiredStateFlowFactory stateFlowFactory) {
    this.flowFactories = Map.of(
      ENTITLE, entitleFlowFactory,
      REVOKE, revokeFlowFactory,
      UPGRADE, upgradeFlowFactory,
      STATE, stateFlowFactory);
  }

  /**
   * Creates flow for enabling/disabling/upgrading applications in the {@link EntitlementRequest} object.
   *
   * @param request - {@link EntitlementRequest} object with required information to perform a flow
   * @return created {@link Flow} object to be executed
   */
  public Flow createFlow(EntitlementRequest request) {
    return flowFactories.get(request.getType()).createFlow(request);
  }
}
