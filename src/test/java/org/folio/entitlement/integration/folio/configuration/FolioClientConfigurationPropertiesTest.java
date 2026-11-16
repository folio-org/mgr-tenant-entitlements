package org.folio.entitlement.integration.folio.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class FolioClientConfigurationPropertiesTest {

  @Test
  void getTenantJob_positive_defaults() {
    var tenantJob = new FolioClientConfigurationProperties().getTenantJob();

    assertThat(tenantJob.getTimeout()).isEqualTo(Duration.ofMinutes(30));
    assertThat(tenantJob.getWait()).isEqualTo(Duration.ofSeconds(20));
    assertThat(tenantJob.getDelay()).isEqualTo(Duration.ofSeconds(1));
    assertThat(tenantJob.getMaxDelay()).isEqualTo(Duration.ofSeconds(10));
    assertThat(tenantJob.getMultiplier()).isEqualTo(2.0);
  }
}
