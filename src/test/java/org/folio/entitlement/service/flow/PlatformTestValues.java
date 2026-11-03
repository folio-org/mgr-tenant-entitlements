package org.folio.entitlement.service.flow;

import static org.folio.entitlement.domain.model.PlatformStageContext.PARAM_PLATFORM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;

import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.model.PlatformEntitlementRequest;
import org.folio.entitlement.domain.model.PlatformStageContext;
import org.folio.flow.api.StageContext;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PlatformTestValues {

  public static PlatformEntitlementRequest platformRequest(String applicationId, EntitlementRequestType type, String token) {
    return PlatformEntitlementRequest.builder()
      .applicationId(applicationId)
      .type(type)
      .okapiToken(token)
      .build();
  }

  public static PlatformStageContext platformContext(PlatformEntitlementRequest request) {
    return PlatformStageContext.decorate(
      StageContext.of(FLOW_ID, Map.of(PARAM_PLATFORM_REQUEST, request), new HashMap<>()));
  }
}
