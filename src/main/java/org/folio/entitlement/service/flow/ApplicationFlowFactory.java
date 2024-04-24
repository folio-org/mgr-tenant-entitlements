package org.folio.entitlement.service.flow;

import java.util.Map;
import java.util.UUID;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.flow.api.Flow;
import org.folio.flow.model.FlowExecutionStrategy;

public interface ApplicationFlowFactory {

  /**
   * Creates a {@link Flow} object for application operation (entitle, upgrade, or revoke).
   *
   * @param flowId - full application flow identifier as {@link UUID} object
   * @param additionalFlowParameters - map with additional flow parameters
   * @return created {@link Flow} for future execution
   */
  Flow createFlow(Object flowId, FlowExecutionStrategy strategy, Map<?, ?> additionalFlowParameters);

  /**
   * Returns entitlement type for flow factory (must not repeat).
   *
   * @return {@link EntitlementType} as key to identify what flow to execute for entitlement request
   */
  EntitlementType getEntitlementType();
}
