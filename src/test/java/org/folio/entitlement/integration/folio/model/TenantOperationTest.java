package org.folio.entitlement.integration.folio.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class TenantOperationTest {

  @Test
  void completed_positive_isNotAsync() {
    var operation = TenantOperation.completed();

    assertThat(operation.isAsync()).isFalse();
    assertThat(operation.jobId()).isNull();
  }

  @Test
  void accepted_positive_isAsyncWithJobId() {
    var operation = TenantOperation.accepted("job-1");

    assertThat(operation.isAsync()).isTrue();
    assertThat(operation.jobId()).isEqualTo("job-1");
  }

  @Test
  void accepted_negative_nullJobId() {
    assertThatThrownBy(() -> TenantOperation.accepted(null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("jobId must not be null");
  }
}
