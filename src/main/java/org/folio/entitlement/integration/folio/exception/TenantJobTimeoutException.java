package org.folio.entitlement.integration.folio.exception;

import static java.lang.String.format;

import java.io.Serial;
import java.time.Duration;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.integration.folio.model.ModuleRequest;

/**
 * The tenant job did not complete within the configured timeout.
 */
public class TenantJobTimeoutException extends TenantJobException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates an exception for a job that did not complete in time.
   *
   * @param request - module request the job belongs to
   * @param jobId - tenant job identifier
   * @param timeout - the timeout that elapsed
   */
  public TenantJobTimeoutException(ModuleRequest request, String jobId, Duration timeout) {
    super(format("Tenant job %s of module %s did not complete within %s", jobId, request.getModuleId(), timeout),
      request, jobId, new Parameter().key("timeout").value(timeout.toString()));
  }
}
