package org.folio.entitlement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.UPGRADE;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;

import java.util.List;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class PlatformEntitlementRequestTest {

  @Test
  void toEntitlementRequest_positive_hasNoTenant() {
    var request = PlatformEntitlementRequest.builder()
      .applicationIds(List.of("app-platform-1.0.0", "other-platform-1.0.0"))
      .type(UPGRADE)
      .okapiToken(OKAPI_TOKEN)
      .build();

    var result = request.toEntitlementRequest();

    assertThat(result.getTenantId()).isNull();
    assertThat(result.getType()).isEqualTo(UPGRADE);
    assertThat(result.getApplications()).containsExactly("app-platform-1.0.0", "other-platform-1.0.0");
    assertThat(result.getOkapiToken()).isEqualTo(OKAPI_TOKEN);
  }
}
