package org.folio.entitlement.integration.folio.exception;

import static java.lang.String.format;

import java.io.Serial;
import java.time.Duration;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.integration.folio.model.ModuleRequest;

/**
 * The tenant job did not complete within the configured timeout.
 */
@SuppressWarnings("java:S110")
public class TenantJobTimeoutException extends TenantJobException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates an exception for a job that did not complete in time, while every read of the job succeeded.
   *
   * @param request - module request the job belongs to
   * @param jobId - tenant job identifier
   * @param timeout - the timeout that elapsed
   */
  public TenantJobTimeoutException(ModuleRequest request, String jobId, Duration timeout) {
    this(request, jobId, timeout, null);
  }

  /**
   * Creates an exception for a job that did not complete in time.
   *
   * @param request - module request the job belongs to
   * @param jobId - tenant job identifier
   * @param timeout - the timeout that elapsed
   * @param lastFailure - the failure of the last job read, or {@code null} if the last read succeeded; it tells a
   *   module that was unavailable apart from one that was only slow
   */
  public TenantJobTimeoutException(ModuleRequest request, String jobId, Duration timeout, Throwable lastFailure) {
    super(format("Tenant job %s of module %s did not complete within %s", jobId, request.getModuleId(), timeout),
      lastFailure, request, jobId, extraParameters(timeout, lastFailure));
  }

  /**
   * Builds the {@code timeout} parameter and, when the last read failed, the {@code lastError} parameter.
   *
   * @param timeout - the timeout that elapsed
   * @param lastFailure - the failure of the last job read, may be {@code null}
   * @return additional error parameters
   */
  private static Parameter[] extraParameters(Duration timeout, Throwable lastFailure) {
    var timeoutParameter = new Parameter().key("timeout").value(timeout.toString());
    if (lastFailure == null) {
      return new Parameter[] {timeoutParameter};
    }

    return new Parameter[] {timeoutParameter, new Parameter().key("lastError").value(lastFailure.getMessage())};
  }
}
