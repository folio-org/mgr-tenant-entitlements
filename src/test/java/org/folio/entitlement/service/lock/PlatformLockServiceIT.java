package org.folio.entitlement.service.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;

import java.util.UUID;
import org.folio.entitlement.exception.PlatformOperationInProgressException;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

/**
 * Verifies the platform lock against a real Postgres: that pg_locks reports the held lock, that a second holder fails
 * fast, and that the probe used by tenant operations acquires nothing.
 */
@IntegrationTest
@SqlMergeMode(MERGE)
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
@TestPropertySource(properties = {
  "application.apigw.enabled=false",
  "application.keycloak.enabled=false"
})
class PlatformLockServiceIT extends BaseIntegrationTest {

  @Autowired private PlatformLockService platformLockService;

  @Test
  void acquire_positive_lockIsVisibleToTenantProbeAndExclusive() {
    var holder = UUID.randomUUID();
    platformLockService.acquire(holder);
    try {
      assertThat(platformLockService.isHeld()).isTrue();
      assertThatThrownBy(platformLockService::assertNotHeld)
        .isInstanceOf(PlatformOperationInProgressException.class);
      assertThatThrownBy(() -> platformLockService.acquire(UUID.randomUUID()))
        .isInstanceOf(PlatformOperationInProgressException.class);
    } finally {
      platformLockService.release(holder);
    }

    assertThat(platformLockService.isHeld()).isFalse();
  }

  @Test
  void assertNotHeld_positive_probeAcquiresNothing() {
    assertThat(platformLockService.isHeld()).isFalse();
    assertThatCode(platformLockService::assertNotHeld).doesNotThrowAnyException();

    // the probe must not have taken the lock, so a platform operation can still acquire it
    var holder = UUID.randomUUID();
    assertThatCode(() -> platformLockService.acquire(holder)).doesNotThrowAnyException();
    platformLockService.release(holder);
  }

  @Test
  void release_positive_isIdempotentAndReleasesForNextOperation() {
    var holder = UUID.randomUUID();
    platformLockService.acquire(holder);
    platformLockService.release(holder);
    platformLockService.release(holder);

    assertThat(platformLockService.isHeld()).isFalse();

    var nextHolder = UUID.randomUUID();
    assertThatCode(() -> platformLockService.acquire(nextHolder)).doesNotThrowAnyException();
    platformLockService.release(nextHolder);
  }
}
