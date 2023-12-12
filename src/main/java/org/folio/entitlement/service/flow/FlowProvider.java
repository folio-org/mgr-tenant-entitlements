package org.folio.entitlement.service.flow;

import java.util.UUID;
import org.folio.flow.api.Flow;
import org.folio.flow.model.FlowExecutionStrategy;

public interface FlowProvider {

  /**
   * Creates a {@link Flow} object for application installation / uninstallation.
   *
   * @param applicationFlowId - application flow identifier as {@link UUID} object
   * @param applicationId - application identifier as {@link String} object
   * @param strategy - flow execution strategy as {@link FlowExecutionStrategy} enum value
   * @return created {@link Flow} for future execution
   */
  Flow prepareFlow(UUID applicationFlowId, String applicationId, FlowExecutionStrategy strategy);
}
