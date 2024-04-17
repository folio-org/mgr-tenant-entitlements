package org.folio.entitlement.service.flow;

import java.util.Map;
import java.util.UUID;
import org.folio.flow.api.Flow;
import org.folio.flow.model.FlowExecutionStrategy;

public interface ApplicationFlowFactory {

  /**
   * Creates a {@link Flow} object for application installation.
   *
   * @param flowId - full application flow identifier as {@link UUID} object
   * @param additionalFlowParameters - map with additional flow parameters
   * @return created {@link Flow} for future execution
   */
  Flow createFlow(Object flowId, FlowExecutionStrategy strategy, Map<?, ?> additionalFlowParameters);
}
