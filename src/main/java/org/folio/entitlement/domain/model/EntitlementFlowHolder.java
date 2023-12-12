package org.folio.entitlement.domain.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.ExtendedEntitlements;
import org.folio.flow.api.Flow;

@Data
@RequiredArgsConstructor(staticName = "of")
public final class EntitlementFlowHolder {

  /**
   * Flow to be executed.
   */
  private final Flow flow;

  /**
   * List with prepared {@link ExtendedEntitlements} values.
   */
  private final ExtendedEntitlements entitlements;
}
