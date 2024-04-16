package org.folio.entitlement.integration.folio;

import java.util.UUID;
import org.folio.flow.api.AbstractStageContextWrapper;
import org.folio.flow.api.StageContext;

public class IdentifiableStageContext extends AbstractStageContextWrapper {

  protected IdentifiableStageContext(StageContext stageContext) {
    super(stageContext);
  }

  /**
   * Returns current flow identifier.
   * <p>
   * For global flow it returns root flow id, for application flow it should return application flow id.
   * </p>
   *
   * @return flow identifier as {@link UUID} object
   */
  public UUID getCurrentFlowId() {
    return UUID.fromString(context.flowId());
  }
}
