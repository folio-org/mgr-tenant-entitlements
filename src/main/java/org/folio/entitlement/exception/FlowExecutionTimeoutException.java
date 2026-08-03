package org.folio.entitlement.exception;

import static java.lang.String.format;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FAILED;

import java.io.Serial;
import java.time.Duration;
import java.util.UUID;
import lombok.Getter;

/**
 * Thrown when a synchronously executed flow did not finish within the configured execution timeout.
 *
 * <p>Must not extend {@code StageExecutionException}: that routes it to
 * {@code ApiExceptionHandler#handleStageExecutionException}, which returns an internal server error without a flow
 * identifier when stage results are empty - always the case for a timed-out flow.</p>
 */
@Getter
public class FlowExecutionTimeoutException extends RuntimeException {

  @Serial private static final long serialVersionUID = 5063185547639041269L;

  private final UUID flowId;
  private final transient Duration timeout;

  public FlowExecutionTimeoutException(UUID flowId, Duration timeout, Throwable cause) {
    super(format("Flow '%s' finished with status: %s", flowId, FAILED), cause);

    this.flowId = flowId;
    this.timeout = timeout;
  }
}
