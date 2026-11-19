package org.folio.entitlement.integration.folio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.commons.lang3.SerializationException;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.folio.configuration.FolioClientConfigurationProperties.TenantJobProperties;
import org.folio.entitlement.integration.folio.exception.TenantJobFailedException;
import org.folio.entitlement.integration.folio.exception.TenantJobPollingException;
import org.folio.entitlement.integration.folio.exception.TenantJobTimeoutException;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.integration.folio.model.TenantJob;
import org.folio.entitlement.integration.folio.model.TenantOperation;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@UnitTest
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class TenantJobPollerTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final String JOB_ID = "6a3b3c1e-1111-4222-8333-444455556666";
  private static final String JOB_ERROR = "relation does not exist";

  @Mock private FolioTenantApiClient client;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(client);
  }

  @Test
  void await_positive_syncOperationIsNotPolled() {
    var poller = createPoller(Duration.ofSeconds(5));

    poller.await(request(), TenantOperation.completed());

    verifyNoInteractions(client);
  }

  @Test
  void await_positive_completesAfterPendingPolls() {
    var request = request();
    var poller = createPoller(Duration.ofSeconds(5));
    stubPoll(request).thenReturn(job(false, null), job(false, null), job(true, null));

    poller.await(request, TenantOperation.accepted(JOB_ID));

    verifyPolls(request, 3);
  }

  @Test
  void await_positive_jobWithoutCompleteFlagIsInProgress() {
    var request = request();
    var poller = createPoller(Duration.ofSeconds(5));
    var jobWithoutFlag = new TenantJob();
    jobWithoutFlag.setId(JOB_ID);
    stubPoll(request).thenReturn(jobWithoutFlag, job(true, null));

    poller.await(request, TenantOperation.accepted(JOB_ID));

    verifyPolls(request, 2);
  }

  @Test
  void await_positive_waitIsTheConfiguredLongPollTime() {
    var request = request();
    var poller = createPoller(Duration.ofSeconds(5));
    stubPoll(request).thenReturn(job(true, null));

    poller.await(request, TenantOperation.accepted(JOB_ID));

    verify(client).getTenantJob(request, JOB_ID, Duration.ofMillis(50));
  }

  @Test
  void await_negative_failedJobFailsWithJobError() {
    var request = request();
    var poller = createPoller(Duration.ofSeconds(5));
    stubPoll(request).thenReturn(job(true, JOB_ERROR));

    assertThatThrownBy(() -> poller.await(request, TenantOperation.accepted(JOB_ID)))
      .isInstanceOfSatisfying(TenantJobFailedException.class, error -> {
        assertThat(error).hasMessageContaining(MODULE_ID).hasMessageContaining(JOB_ERROR).hasNoCause();
        assertThat(error.getCauseHttpStatus()).isNull();
        assertThat(error.getErrors()).extracting(Parameter::getKey, Parameter::getValue)
          .contains(tuple("moduleId", MODULE_ID), tuple("jobId", JOB_ID), tuple("cause", JOB_ERROR));
      });
  }

  @Test
  void await_negative_jobFailsAfterPendingPolls() {
    var request = request();
    var poller = createPoller(Duration.ofSeconds(5));
    stubPoll(request).thenReturn(job(false, null), job(false, null), job(true, JOB_ERROR));
    var operation = TenantOperation.accepted(JOB_ID);

    assertThatThrownBy(() -> poller.await(request, operation))
      .isInstanceOf(TenantJobFailedException.class)
      .hasMessageContaining(JOB_ERROR);

    verifyPolls(request, 3);
  }

  @Test
  void await_negative_neverCompletingJobTimesOut() {
    var request = request();
    var poller = createPoller(Duration.ofMillis(150));
    stubPoll(request).thenReturn(job(false, null));

    assertThatThrownBy(() -> poller.await(request, TenantOperation.accepted(JOB_ID)))
      .isInstanceOfSatisfying(TenantJobTimeoutException.class, error -> {
        assertThat(error).hasMessageContaining(MODULE_ID).hasMessageContaining(JOB_ID);
        assertThat(error.getCauseHttpStatus()).isNull();
        assertThat(error).hasNoCause();
        assertThat(error.getErrors()).extracting(Parameter::getKey).doesNotContain("lastError");
      });
  }

  @Test
  void await_positive_serverErrorIsRetried() {
    var request = request();
    var poller = createPoller(Duration.ofSeconds(5));
    stubPoll(request).thenThrow(new IntegrationException("busy", List.of(), 503)).thenReturn(job(true, null));

    poller.await(request, TenantOperation.accepted(JOB_ID));

    verifyPolls(request, 2);
  }

  @Test
  void await_negative_persistentServerErrorTimesOutWithLastError() {
    var request = request();
    var poller = createPoller(Duration.ofMillis(150));
    var serverError = new IntegrationException("busy", List.of(), 503);
    stubPoll(request).thenThrow(serverError);
    var operation = TenantOperation.accepted(JOB_ID);

    assertThatThrownBy(() -> poller.await(request, operation))
      .isInstanceOfSatisfying(TenantJobTimeoutException.class, error -> {
        assertThat(error).hasMessageContaining(MODULE_ID).hasCause(serverError);
        assertThat(error.getErrors()).extracting(Parameter::getKey, Parameter::getValue)
          .contains(tuple("lastError", "busy"));
      });

    verify(client, atLeast(2)).getTenantJob(eq(request), eq(JOB_ID), any(Duration.class));
  }

  @ParameterizedTest
  @MethodSource("transientClientErrorProvider")
  void await_positive_transientClientErrorIsRetried(int status) {
    var request = request();
    var poller = createPoller(Duration.ofSeconds(5));
    stubPoll(request).thenThrow(new IntegrationException("try later", List.of(), status)).thenReturn(job(true, null));

    poller.await(request, TenantOperation.accepted(JOB_ID));

    verifyPolls(request, 2);
  }

  @ParameterizedTest
  @MethodSource("permanentClientErrorProvider")
  void await_negative_permanentClientErrorIsNotRetried(int status) {
    var request = request();
    var poller = createPoller(Duration.ofSeconds(5));
    var clientError = new IntegrationException("rejected", List.of(), status);
    stubPoll(request).thenThrow(clientError);
    var operation = TenantOperation.accepted(JOB_ID);

    assertThatThrownBy(() -> poller.await(request, operation))
      .isInstanceOf(TenantJobPollingException.class)
      .hasCause(clientError);

    verifyPolls(request, 1);
  }

  @Test
  void await_positive_progressOfPendingJobIsLoggedEverySixthRead(CapturedOutput output) {
    var request = request();
    var poller = createPoller(Duration.ofSeconds(5));
    var reads = new AtomicInteger();
    stubPoll(request).thenAnswer(invocation -> job(reads.incrementAndGet() > 13, null));

    poller.await(request, TenantOperation.accepted(JOB_ID));

    assertThat(output.getAll().lines().filter(line -> line.contains("Tenant job is still in progress"))).hasSize(2);
    verifyPolls(request, 14);
  }

  @Test
  void await_positive_transientFailuresAreLoggedFirstAndEveryTenth(CapturedOutput output) {
    var request = request();
    var poller = createPoller(Duration.ofSeconds(5));
    var reads = new AtomicInteger();
    stubPoll(request).thenAnswer(invocation -> {
      if (reads.incrementAndGet() <= 11) {
        throw new IntegrationException("busy", List.of(), 503);
      }
      return job(true, null);
    });

    poller.await(request, TenantOperation.accepted(JOB_ID));

    assertThat(output.getAll().lines().filter(line -> line.contains("Failed to read tenant job")))
      .satisfiesExactly(
        first -> assertThat(first).contains("failures = 1,").contains("error = busy"),
        tenth -> assertThat(tenth).contains("failures = 10,"));
    verifyPolls(request, 12);
  }

  @Test
  void await_positive_ioErrorIsRetried() {
    var request = request();
    var poller = createPoller(Duration.ofSeconds(5));
    stubPoll(request).thenThrow(new IntegrationException("io", new IOException("reset"))).thenReturn(job(true, null));

    poller.await(request, TenantOperation.accepted(JOB_ID));

    verifyPolls(request, 2);
  }

  @Test
  void await_negative_clientErrorIsNotRetried() {
    var request = request();
    var poller = createPoller(Duration.ofSeconds(5));
    var clientError = new IntegrationException("not found", List.of(), 404);
    stubPoll(request).thenThrow(clientError);
    var operation = TenantOperation.accepted(JOB_ID);

    assertThatThrownBy(() -> poller.await(request, operation))
      .isInstanceOf(TenantJobPollingException.class)
      .hasMessageContaining(MODULE_ID)
      .hasCause(clientError);

    verifyPolls(request, 1);
  }

  @Test
  void await_negative_unreadableJobResponseIsNotRetried() {
    var request = request();
    var poller = createPoller(Duration.ofSeconds(5));
    var readError = new SerializationException("Failed to deserialize value");
    stubPoll(request).thenThrow(readError);
    var operation = TenantOperation.accepted(JOB_ID);

    assertThatThrownBy(() -> poller.await(request, operation))
      .isInstanceOf(TenantJobPollingException.class)
      .hasMessageContaining(JOB_ID)
      .hasCause(readError);

    verifyPolls(request, 1);
  }

  @Test
  void await_positive_waitIsCappedByRemainingTime() {
    var request = request();
    var properties = properties(Duration.ofMillis(300));
    properties.setWait(Duration.ofSeconds(10));
    var poller = new TenantJobPoller(client, properties);
    stubPoll(request).thenReturn(job(true, null));

    poller.await(request, TenantOperation.accepted(JOB_ID));

    var waitCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(client).getTenantJob(eq(request), eq(JOB_ID), waitCaptor.capture());
    assertThat(waitCaptor.getValue()).isLessThanOrEqualTo(Duration.ofMillis(300));
  }

  @Test
  void await_negative_interruptedPollingFails() {
    var request = request();
    var poller = createPoller(Duration.ofSeconds(5));
    stubPoll(request).thenReturn(job(false, null));
    var operation = TenantOperation.accepted(JOB_ID);

    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> poller.await(request, operation))
        .isInstanceOf(TenantJobPollingException.class)
        .hasMessageContaining("interrupted")
        .hasCauseInstanceOf(TenantJobInProgressException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  private static Stream<Integer> transientClientErrorProvider() {
    return Stream.of(408, 425, 429);
  }

  private static Stream<Integer> permanentClientErrorProvider() {
    return Stream.of(400, 401, 403, 404);
  }

  private OngoingStubbing<TenantJob> stubPoll(ModuleRequest request) {
    return when(client.getTenantJob(eq(request), eq(JOB_ID), any(Duration.class)));
  }

  private void verifyPolls(ModuleRequest request, int count) {
    verify(client, times(count)).getTenantJob(eq(request), eq(JOB_ID), any(Duration.class));
  }

  private TenantJobPoller createPoller(Duration timeout) {
    return new TenantJobPoller(client, properties(timeout));
  }

  private static TenantJobProperties properties(Duration timeout) {
    var properties = new TenantJobProperties();
    properties.setTimeout(timeout);
    properties.setWait(Duration.ofMillis(50));
    properties.setDelay(Duration.ofMillis(1));
    properties.setMaxDelay(Duration.ofMillis(5));
    properties.setMultiplier(1.0);
    return properties;
  }

  private static TenantJob job(boolean complete, String error) {
    var job = new TenantJob();
    job.setId(JOB_ID);
    job.setComplete(complete);
    job.setError(error);
    return job;
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
