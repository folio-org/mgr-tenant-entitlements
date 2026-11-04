package org.folio.entitlement.service.flow;

import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.PlatformEntitlementRequest;
import org.folio.flow.api.StageContext;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PlatformTestValues {

  public static PlatformEntitlementRequest platformRequest(EntitlementRequestType type, String token,
    String... applicationIds) {
    return PlatformEntitlementRequest.builder()
      .applicationIds(List.of(applicationIds))
      .type(type)
      .okapiToken(token)
      .build();
  }

  public static CommonStageContext platformContext(PlatformEntitlementRequest request) {
    return CommonStageContext.decorate(
      StageContext.of(FLOW_ID, Map.of(PARAM_REQUEST, request.toEntitlementRequest()), new HashMap<>()));
  }
}
