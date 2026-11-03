package org.folio.entitlement.service.flow;

import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.UPGRADE;

import java.util.Map;
import java.util.UUID;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.model.PlatformEntitlementRequest;
import org.folio.flow.api.Flow;
import org.springframework.stereotype.Component;

/**
 * Selects the platform flow factory by the type of the request.
 */
@Component
public class PlatformFlowProvider {

  private final Map<EntitlementRequestType, PlatformFlowFactory> flowFactories;

  public PlatformFlowProvider(PlatformEntitleFlowFactory entitleFlowFactory,
    PlatformUpgradeFlowFactory upgradeFlowFactory) {
    this.flowFactories = Map.of(
      ENTITLE, entitleFlowFactory,
      UPGRADE, upgradeFlowFactory);
  }

  /**
   * Creates the flow that performs the platform request.
   *
   * @param flowId - identifier of the flow
   * @param request - platform entitlement request
   * @return created {@link Flow} object to be executed
   */
  public Flow createFlow(UUID flowId, PlatformEntitlementRequest request) {
    return flowFactories.get(request.getType()).createFlow(flowId, request);
  }
}
