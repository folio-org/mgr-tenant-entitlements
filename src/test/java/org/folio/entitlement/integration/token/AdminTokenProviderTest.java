package org.folio.entitlement.integration.token;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class AdminTokenProviderTest {

  @Test
  void isSharedIdentity_positive_defaultsToFalseUntilImplementationOptsIn() {
    AdminTokenProvider provider = userToken -> userToken;

    assertThat(provider.isSharedIdentity()).isFalse();
  }
}
