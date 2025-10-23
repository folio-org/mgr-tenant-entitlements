package org.folio.entitlement.integration.okapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;

import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@UnitTest
class RequestAdminTokenProviderTest {

  private RequestAdminTokenProvider requestAdminTokenProvider;

  @BeforeEach
  void setUp() {
    requestAdminTokenProvider = new RequestAdminTokenProvider();
  }

  @Test
  void getToken_positive() {
    var result = requestAdminTokenProvider.getToken(OKAPI_TOKEN);

    assertThat(result).isEqualTo(OKAPI_TOKEN);
  }
}
