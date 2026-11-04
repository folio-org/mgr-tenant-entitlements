package org.folio.entitlement.domain.model;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.folio.entitlement.domain.dto.EntitlementRequestType;

/**
 * A deployment-wide request to entitle or upgrade a platform-scoped application. It has no tenant.
 */
@Value
@Builder
public class PlatformEntitlementRequest {

  /**
   * Full application identifiers as name-version. The request is applied to all of them or to none.
   */
  List<String> applicationIds;

  /**
   * {@code ENTITLE} to entitle the application for the first time, {@code UPGRADE} to replace the entitled version.
   */
  EntitlementRequestType type;

  /**
   * Okapi token used to load the application descriptor.
   */
  String okapiToken;
}
