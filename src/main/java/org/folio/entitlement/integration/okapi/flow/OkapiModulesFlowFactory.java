package org.folio.entitlement.integration.okapi.flow;

import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.flow.api.Flow;

public interface OkapiModulesFlowFactory {

  /**
   * Creates flow for {@link EntitlementType} using given context.
   *
   * @param context - {@link ApplicationStageContext} object
   * @return created {@link Flow} to be executed
   */
  Flow createFlow(ApplicationStageContext context);

  /**
   * Returns entitlement type for flow factory (must not repeat).
   *
   * @return {@link EntitlementType} as key to identify what flow to execute for entitlement request
   */
  EntitlementType getEntitlementType();
}
