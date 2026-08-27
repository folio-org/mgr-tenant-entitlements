package org.folio.entitlement.service.stage;

import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.model.IdentifiableStageContext;

public interface FlowFinalizerStatusProvider<C extends IdentifiableStageContext> {

  ExecutionStatus getFinalStatus(C context);
}
