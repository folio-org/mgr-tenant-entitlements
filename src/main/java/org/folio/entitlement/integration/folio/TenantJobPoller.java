package org.folio.entitlement.integration.folio;

import static java.lang.Boolean.TRUE;
import static java.lang.String.format;

import java.time.Duration;
import java.time.Instant;
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
    var timedOut = new AtomicBoolean();
    try {
      createRetryTemplate(timedOut).execute(() -> poll(request, jobId, startedAt));
      log.info("Tenant job completed: moduleId = {}, jobId = {}, tenant = {}, elapsed = {}",
        request.getModuleId(), jobId, request.getTenantName(), Duration.between(startedAt, Instant.now()));
    } catch (RetryException e) {
      throw toFailure(request, jobId, timedOut, e);
    }
  }

  /**
   * Creates the retry template that repeats a poll while the job is in progress or a read failed transiently, until
   * the overall timeout.
   *
   * @param timedOut - flag set when the retry policy gives up because the timeout elapsed
   * @return configured retry template
   */
  private RetryTemplate createRetryTemplate(AtomicBoolean timedOut) {
    var policy = RetryPolicy.builder()
      .maxRetries(Long.MAX_VALUE)
      .delay(properties.getDelay())
      .multiplier(properties.getMultiplier())
      .maxDelay(properties.getMaxDelay())
      .timeout(properties.getTimeout())
      .includes(TenantJobInProgressException.class, TenantJobTransientException.class)
      .build();

    var template = new RetryTemplate(policy);
    template.setRetryListener(new RetryListener() {
      @Override
      public void onRetryPolicyTimeout(RetryPolicy retryPolicy, Retryable<?> retryable, RetryException exception) {
        timedOut.set(true);
      }
    });
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
   * @throws TenantJobTransientException if the read failed in a way worth repeating (I/O error or server error)
   * @throws TenantJobPollingException if the module rejected the read (client error), which is not repeated
   */
  private TenantJob fetch(ModuleRequest request, String jobId, Duration wait) {
    try {
      return client.getTenantJob(request, jobId, wait);
    } catch (IntegrationException e) {
      var status = e.getCauseHttpStatus();
      if (status == null || status >= 500) {
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
   * @param timedOut - flag telling that the overall timeout elapsed
   * @param exception - exception thrown by the retry template, its cause is the last failure
   * @return timeout exception, polling exception for an interrupted wait, or the last failure itself
   */
  private IntegrationException toFailure(ModuleRequest request, String jobId, AtomicBoolean timedOut,
    RetryException exception) {
    var cause = exception.getCause();
    if (timedOut.get()) {
      return new TenantJobTimeoutException(request, jobId, properties.getTimeout());
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
}
