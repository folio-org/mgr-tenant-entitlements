package org.folio.entitlement.configuration.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.folio.entitlement.integration.token.AdminTokenProvider;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies that caching follows the identity used for downstream calls.
 */
@UnitTest
@ExtendWith(MockitoExtension.class)
class EntitlementCachePolicyTest {

  @Mock private AdminTokenProvider adminTokenProvider;
  @InjectMocks private EntitlementCachePolicy policy;

  @Test
  void isEnabled_positive_sharedIdentity() {
    when(adminTokenProvider.isSharedIdentity()).thenReturn(true);

    assertThat(policy.isEnabled()).isTrue();
  }

  @Test
  void isEnabled_positive_callerIdentityIsForwarded() {
    when(adminTokenProvider.isSharedIdentity()).thenReturn(false);

    assertThat(policy.isEnabled()).isFalse();
  }
}
