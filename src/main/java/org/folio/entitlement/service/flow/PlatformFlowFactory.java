package org.folio.entitlement.service.flow;

import java.util.UUID;
import org.folio.entitlement.domain.model.PlatformEntitlementRequest;
import org.folio.flow.api.Flow;

/**
 * Builds the flow that validates and commits one kind of deployment-wide platform entitlement request.
 */
public interface PlatformFlowFactory {

  Flow createFlow(UUID flowId, PlatformEntitlementRequest request);
}
