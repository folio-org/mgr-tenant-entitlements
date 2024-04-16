package org.folio.entitlement.service.flow;

import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;

import java.util.Map;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.flow.api.Flow;
import org.springframework.stereotype.Component;

@Component
public class ApplicationFlowFactoryProvider {

  private final Map<EntitlementType, ApplicationFlowFactory> applicationFlowProviders;

  /**
   * Injects beans from spring context.
   *
   * @param entitleApplicationFlowFactory - {@link EntitleApplicationFlowFactory} bean
   * @param upgradeApplicationFlowFactory - {@link UpgradeApplicationFlowFactory} bean
   * @param revokeApplicationFlowFactory - {@link RevokeApplicationFlowFactory} bean
   */
  public ApplicationFlowFactoryProvider(
    EntitleApplicationFlowFactory entitleApplicationFlowFactory,
    UpgradeApplicationFlowFactory upgradeApplicationFlowFactory,
    RevokeApplicationFlowFactory revokeApplicationFlowFactory) {
    this.applicationFlowProviders = Map.of(
      ENTITLE, entitleApplicationFlowFactory,
      UPGRADE, upgradeApplicationFlowFactory,
      REVOKE, revokeApplicationFlowFactory);
  }

  /**
   * Creates flow for enabling/disabling/upgrading applications in the {@link EntitlementRequest} object.
   *
   * @param type - {@link EntitlementType} to determine what factory must be used to build a flow
   * @return created {@link Flow} object to be executed
   */
  public ApplicationFlowFactory get(EntitlementType type) {
    return applicationFlowProviders.get(type);
  }
}
