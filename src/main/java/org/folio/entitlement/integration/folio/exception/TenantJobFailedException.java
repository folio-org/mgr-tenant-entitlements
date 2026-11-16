package org.folio.entitlement.integration.folio.exception;

import static java.lang.String.format;

import java.io.Serial;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.integration.folio.model.TenantJob;

/**
 * The module completed the tenant job with an error.
 */
public class TenantJobFailedException extends TenantJobException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates an exception for a job that completed with an error.
   *
   * @param request - module request the job belongs to
   * @param jobId - tenant job identifier
   * @param job - completed job with its error and messages
   */
  public TenantJobFailedException(ModuleRequest request, String jobId, TenantJob job) {
    super(format("Tenant job %s of module %s failed: %s", jobId, request.getModuleId(), job.getError()),
      request, jobId, errorParameters(job));
  }

  /**
   * Builds the {@code cause} and {@code messages} parameters from the job.
   *
   * @param job - completed job
   * @return error parameters with the job's error and its messages joined by {@code "; "}
   */
  private static Parameter[] errorParameters(TenantJob job) {
    var messages = job.getMessages() == null ? "" : String.join("; ", job.getMessages());
    return new Parameter[] {
      new Parameter().key("cause").value(job.getError()),
      new Parameter().key("messages").value(messages)
    };
  }
}
