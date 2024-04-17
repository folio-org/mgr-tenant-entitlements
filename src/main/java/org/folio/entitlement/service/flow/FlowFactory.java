package org.folio.entitlement.service.flow;

import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.flow.api.Flow;

public interface FlowFactory {

  /**
   * Creates entitle/upgrade/revoke flow from entitlement request.
   *
   * @param request - entitlement request
   * @return created {@link Flow} to execute
   */
  Flow createFlow(EntitlementRequest request);
}
