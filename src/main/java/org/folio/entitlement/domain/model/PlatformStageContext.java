package org.folio.entitlement.domain.model;

import org.folio.flow.api.StageContext;

public class PlatformStageContext extends IdentifiableStageContext {

  public static final String PARAM_PLATFORM_REQUEST = "platformEntitlementRequest";

  /**
   * Creates {@link PlatformStageContext} from {@link StageContext}.
   *
   * @param stageContext - {@link StageContext} object
   */
  public PlatformStageContext(StageContext stageContext) {
    super(stageContext);
  }

  /**
   * Decorates {@link StageContext} with {@link PlatformStageContext} functionality.
   *
   * @param stageContext - {@link StageContext} object
   * @return created {@link PlatformStageContext} decorator object
   */
  public static PlatformStageContext decorate(StageContext stageContext) {
    return new PlatformStageContext(stageContext);
  }

  public PlatformEntitlementRequest getPlatformRequest() {
    return context.getFlowParameter(PARAM_PLATFORM_REQUEST);
  }
}
