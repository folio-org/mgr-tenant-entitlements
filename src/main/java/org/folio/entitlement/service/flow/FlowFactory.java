package org.folio.entitlement.service.flow;

import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.flow.api.Flow;

public interface FlowFactory {

  Flow createFlow(EntitlementRequest request);
}
