package org.folio.entitlement.service.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;

import javax.sql.DataSource;
import org.folio.entitlement.exception.PlatformOperationInProgressException;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

@IntegrationTest
@SqlMergeMode(MERGE)
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
@TestPropertySource(properties = {
  "application.apigw.enabled=false",
  "application.keycloak.enabled=false"
})
class PlatformLockServiceIT extends BaseIntegrationTest {

  @Autowired private PlatformLockService platformLockService;
  @Autowired private DataSource dataSource;

  @Test
  void runExclusively_positive_lockIsVisibleToTenantProbeAndExclusive() {
    var result = platformLockService.runExclusively(() -> {
      assertThat(platformLockService.isHeld()).isTrue();
      assertThatThrownBy(platformLockService::assertNotHeld)
        .isInstanceOf(PlatformOperationInProgressException.class);
      assertThatThrownBy(() -> platformLockService.runExclusively(() -> "second"))
        .isInstanceOf(PlatformOperationInProgressException.class);
      return "done";
    });

    assertThat(result).isEqualTo("done");
    assertThat(platformLockService.isHeld()).isFalse();
  }

  @Test
  void runExclusively_negative_releasesLockAndRethrowsOperationException() {
    var failure = new IllegalStateException("operation failed");

    assertThatThrownBy(() -> platformLockService.runExclusively(() -> {
      throw failure;
    })).isSameAs(failure);

    assertThat(platformLockService.isHeld()).isFalse();
    assertThatCode(() -> platformLockService.runExclusively(() -> "next")).doesNotThrowAnyException();
  }

  @Test
  void runExclusively_positive_returnsConnectionToPoolInAutocommitMode() throws Exception {
    platformLockService.runExclusively(() -> "done");

    try (var connection = dataSource.getConnection()) {
      assertThat(connection.getAutoCommit()).isTrue();
    }
  }

  @Test
  void assertNotHeld_positive_probeAcquiresNothing() {
    assertThat(platformLockService.isHeld()).isFalse();
    assertThatCode(platformLockService::assertNotHeld).doesNotThrowAnyException();

    // the probe must not keep the lock, so a platform operation can still acquire it
    assertThatCode(() -> platformLockService.runExclusively(() -> "done")).doesNotThrowAnyException();
  }
}
