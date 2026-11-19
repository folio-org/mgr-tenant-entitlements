package org.folio.entitlement.integration.folio;

import static java.lang.Boolean.TRUE;
import static java.lang.String.format;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.folio.configuration.FolioClientConfigurationProperties.TenantJobProperties;
import org.folio.entitlement.integration.folio.exception.TenantJobFailedException;
import org.folio.entitlement.integration.folio.exception.TenantJobPollingException;
import org.folio.entitlement.integration.folio.exception.TenantJobTimeoutException;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.integration.folio.model.TenantJob;
import org.folio.entitlement.integration.folio.model.TenantOperation;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;

/**
 * Blocks until an asynchronous tenant job of a module completes, fails, or the configured timeout elapses.
 */
@Log4j2
public class TenantJobPoller {

  /** Client errors that are transient by convention, so reading the job is repeated: timeout, too early, throttled. */
  private static final Set<Integer> TRANSIENT_CLIENT_ERRORS = Set.of(408, 425, 429);
  private static final int TRANSIENT_FAILURE_LOG_INTERVAL = 10;
  private static final int PROGRESS_LOG_INTERVAL = 6;

  private final FolioTenantApiClient client;
  private final TenantJobProperties properties;

  /**
   * Creates a poller.
   *
   * @param client - client used to read tenant jobs
   * @param properties - polling settings, validated when they are bound
   */
  public TenantJobPoller(FolioTenantApiClient client, TenantJobProperties properties) {
    this.client = client;
    this.properties = properties;
  }

  /**
   * Waits for the operation to finish. Returns immediately for operations that are already completed.
   *
   * @param request - module request the operation belongs to
   * @param operation - outcome of the tenant API call
   * @throws TenantJobFailedException if the job completed with an error
   * @throws TenantJobTimeoutException if the job did not complete within the configured timeout
   * @throws TenantJobPollingException if the job cannot be read or polling was interrupted
   */
  public void await(ModuleRequest request, TenantOperation operation) {
    if (!operation.isAsync()) {
      return;
    }

    var jobId = operation.jobId();
    var startedAt = Instant.now();
    var progress = new PollingProgressListener(request, jobId, startedAt);
    try {
      createRetryTemplate(progress).execute(() -> poll(request, jobId, startedAt));
      log.info("Tenant job completed: moduleId = {}, jobId = {}, tenant = {}, elapsed = {}ms",
        request.getModuleId(), jobId, request.getTenantName(), Duration.between(startedAt, Instant.now()).toMillis());
    } catch (RetryException e) {
      throw toFailure(request, jobId, progress.isTimedOut(), e);
    }
  }

  /**
   * Creates the retry template that repeats a poll while the job is in progress or a read failed transiently, until
   * the overall timeout.
   *
   * @param listener - listener that logs the progress and records that the retry policy gave up on the timeout
   * @return configured retry template
   */
  private RetryTemplate createRetryTemplate(PollingProgressListener listener) {
    var policy = RetryPolicy.builder()
      .maxRetries(Long.MAX_VALUE)
      .delay(properties.getDelay())
      .multiplier(properties.getMultiplier())
      .maxDelay(properties.getMaxDelay())
      .timeout(properties.getTimeout())
      .includes(TenantJobInProgressException.class, TenantJobTransientException.class)
      .build();

    var template = new RetryTemplate(policy);
    template.setRetryListener(listener);
    return template;
  }

  /**
   * Reads the job once and interprets its state.
   *
   * @param request - module request the job belongs to
   * @param jobId - tenant job identifier
   * @param startedAt - moment polling started, used to cap the long-poll time
   * @return the completed job
   * @throws TenantJobInProgressException if the job is not complete yet
   * @throws TenantJobFailedException if the job completed with an error
   */
  private TenantJob poll(ModuleRequest request, String jobId, Instant startedAt) {
    var job = fetch(request, jobId, waitTime(startedAt));
    if (!TRUE.equals(job.getComplete())) {
      throw new TenantJobInProgressException();
    }

    if (job.getError() != null) {
      throw new TenantJobFailedException(request, jobId, job);
    }

    return job;
  }

  /**
   * Reads the job from the module and classifies a failed read.
   *
   * @param request - module request the job belongs to
   * @param jobId - tenant job identifier
   * @param wait - long-poll time to ask the module for
   * @return current state of the job
   * @throws TenantJobTransientException if the read failed in a way worth repeating (I/O error, server error, or a
   *   client error that is transient by convention: 408, 425, 429)
   * @throws TenantJobPollingException if the module rejected the read (other client error), which is not repeated
   */
  private TenantJob fetch(ModuleRequest request, String jobId, Duration wait) {
    try {
      return client.getTenantJob(request, jobId, wait);
    } catch (IntegrationException e) {
      var status = e.getCauseHttpStatus();
      if (status == null || status >= 500 || TRANSIENT_CLIENT_ERRORS.contains(status)) {
        throw new TenantJobTransientException(e);
      }
      throw new TenantJobPollingException(request, jobId, e);
    }
  }

  /**
   * Calculates the long-poll time for the next read: the configured one, but never longer than the time left before
   * the timeout.
   *
   * @param startedAt - moment polling started
   * @return long-poll time, zero when the timeout has already elapsed
   */
  private Duration waitTime(Instant startedAt) {
    var remaining = properties.getTimeout().minus(Duration.between(startedAt, Instant.now()));
    if (remaining.isNegative()) {
      return Duration.ZERO;
    }
    return properties.getWait().compareTo(remaining) <= 0 ? properties.getWait() : remaining;
  }

  /**
   * Converts the exhausted retry into the exception reported to the stage.
   *
   * @param request - module request the job belongs to
   * @param jobId - tenant job identifier
   * @param timedOut - telling that the overall timeout elapsed
   * @param exception - exception thrown by the retry template, its cause is the last failure
   * @return timeout exception carrying the last read failure, if any; polling exception for an interrupted wait; or
   *   the last failure itself
   */
  private IntegrationException toFailure(ModuleRequest request, String jobId, boolean timedOut,
    RetryException exception) {
    var cause = exception.getCause();
    if (timedOut) {
      var lastReadFailure = cause instanceof TenantJobTransientException ? cause.getCause() : null;
      return new TenantJobTimeoutException(request, jobId, properties.getTimeout(), lastReadFailure);
    }

    if (Thread.currentThread().isInterrupted()) {
      var message = format("Tenant job polling was interrupted [moduleId: %s, jobId: %s]",
        request.getModuleId(), jobId);
      return new TenantJobPollingException(message, request, jobId, cause);
    }

    return cause instanceof IntegrationException integrationException
      ? integrationException
      : new TenantJobPollingException(request, jobId, cause);
  }

  /**
   * Logs what the retry template does between reads, which the caller cannot see while it is blocked: the first
   * retried and every 10th transient read failure at WARN, and every 6th read of a job that is still in progress at
   * INFO. Also records that the retry policy gave up on the timeout.
   *
   * <p>One instance belongs to one {@link #await} call, which runs on a single thread.</p>
   */
  private static final class PollingProgressListener implements RetryListener {

    private final ModuleRequest request;
    private final String jobId;
    private final Instant startedAt;
    private final AtomicBoolean timedOut = new AtomicBoolean();
    private int transientFailures;
    private int inProgressReads;

    private PollingProgressListener(ModuleRequest request, String jobId, Instant startedAt) {
      this.request = request;
      this.jobId = jobId;
      this.startedAt = startedAt;
    }

    @Override
    public void onRetryFailure(RetryPolicy retryPolicy, Retryable<?> retryable, Throwable throwable) {
      if (throwable instanceof TenantJobTransientException) {
        transientFailures++;
        if (transientFailures == 1 || transientFailures % TRANSIENT_FAILURE_LOG_INTERVAL == 0) {
          log.warn("Failed to read tenant job, will keep polling: moduleId = {}, jobId = {}, tenant = {}, "
            + "failures = {}, elapsed = {}, error = {}", request.getModuleId(), jobId, request.getTenantName(),
            transientFailures, elapsed(), throwable.getCause().getMessage());
        }
      } else if (throwable instanceof TenantJobInProgressException) {
        inProgressReads++;
        if (inProgressReads % PROGRESS_LOG_INTERVAL == 0) {
          log.info("Tenant job is still in progress: moduleId = {}, jobId = {}, tenant = {}, elapsed = {}ms",
            request.getModuleId(), jobId, request.getTenantName(), elapsed().toMillis());
        }
      }
    }

    @Override
    public void onRetryPolicyTimeout(RetryPolicy retryPolicy, Retryable<?> retryable, RetryException exception) {
      timedOut.set(true);
    }

    private boolean isTimedOut() {
      return timedOut.get();
    }

    private Duration elapsed() {
      return Duration.between(startedAt, Instant.now());
    }
  }
}
