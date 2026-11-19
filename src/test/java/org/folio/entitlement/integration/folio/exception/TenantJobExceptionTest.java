package org.folio.entitlement.integration.folio.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.integration.folio.model.TenantJob;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class TenantJobExceptionTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final String JOB_ID = "6a3b3c1e-1111-4222-8333-444455556666";

  @Test
  void failed_positive_carriesErrorAndJoinedMessages() {
    var job = new TenantJob();
    job.setError("relation foo does not exist");
    job.setMessages(Arrays.asList("schema upgrade failed", "rolled back"));

    var exception = new TenantJobFailedException(request(), JOB_ID, job);

    assertThat(exception).hasMessage("Tenant job " + JOB_ID + " of module " + MODULE_ID
      + " failed: relation foo does not exist");
    assertThat(exception.getCauseHttpStatus()).isNull();
    assertThat(exception.getErrors()).extracting(Parameter::getKey, Parameter::getValue).containsExactly(
      tuple("moduleId", MODULE_ID),
      tuple("tenant", TENANT_NAME),
      tuple("jobId", JOB_ID),
      tuple("cause", "relation foo does not exist"),
      tuple("messages", "schema upgrade failed; rolled back"));
  }

  @Test
  void failed_positive_jobWithoutMessages() {
    var job = new TenantJob();
    job.setError("boom");

    var exception = new TenantJobFailedException(request(), JOB_ID, job);

    assertThat(exception.getErrors()).extracting(Parameter::getKey, Parameter::getValue)
      .contains(tuple("cause", "boom"), tuple("messages", ""));
  }

  @Test
  void timeout_positive_carriesTimeout() {
    var exception = new TenantJobTimeoutException(request(), JOB_ID, Duration.ofMinutes(30));

    assertThat(exception).hasMessage("Tenant job " + JOB_ID + " of module " + MODULE_ID
      + " did not complete within PT30M");
    assertThat(exception.getCauseHttpStatus()).isNull();
    assertThat(exception.getErrors()).extracting(Parameter::getKey, Parameter::getValue).containsExactly(
      tuple("moduleId", MODULE_ID),
      tuple("tenant", TENANT_NAME),
      tuple("jobId", JOB_ID),
      tuple("timeout", "PT30M"));
  }

  @Test
  void timeout_positive_carriesLastReadFailure() {
    var lastFailure = new IOException("connection refused");

    var exception = new TenantJobTimeoutException(request(), JOB_ID, Duration.ofMinutes(30), lastFailure);

    assertThat(exception).hasCause(lastFailure);
    assertThat(exception.getCauseHttpStatus()).isNull();
    assertThat(exception.getErrors()).extracting(Parameter::getKey, Parameter::getValue).containsExactly(
      tuple("moduleId", MODULE_ID),
      tuple("tenant", TENANT_NAME),
      tuple("jobId", JOB_ID),
      tuple("timeout", "PT30M"),
      tuple("lastError", "connection refused"));
  }

  @Test
  void polling_positive_carriesCause() {
    var cause = new IOException("reset");

    var exception = new TenantJobPollingException(request(), JOB_ID, cause);

    assertThat(exception).hasMessage("Failed to poll tenant job " + JOB_ID + " of module " + MODULE_ID)
      .hasCause(cause);
    assertThat(exception.getCauseHttpStatus()).isNull();
    assertThat(exception.getErrors()).extracting(Parameter::getKey, Parameter::getValue).containsExactly(
      tuple("moduleId", MODULE_ID),
      tuple("tenant", TENANT_NAME),
      tuple("jobId", JOB_ID));
  }

  private static ModuleRequest request() {
    return ModuleRequest.builder()
      .moduleId(MODULE_ID)
      .location("http://mod-foo:8081")
      .tenantId(TENANT_ID)
      .tenantName(TENANT_NAME)
      .build();
  }
}
