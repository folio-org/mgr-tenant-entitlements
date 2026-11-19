package org.folio.entitlement.integration.folio.exception;

import static java.lang.String.format;

import java.io.Serial;
import org.folio.entitlement.integration.folio.model.ModuleRequest;

/**
 * The tenant job cannot be read, or polling was interrupted.
 */
@SuppressWarnings("java:S110")
public class TenantJobPollingException extends TenantJobException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates an exception for a job that could not be read.
   *
   * @param request - module request the job belongs to
   * @param jobId - tenant job identifier
   * @param cause - the failure that stopped polling
   */
  public TenantJobPollingException(ModuleRequest request, String jobId, Throwable cause) {
    this(format("Failed to poll tenant job %s of module %s", jobId, request.getModuleId()), request, jobId, cause);
  }

  /**
   * Creates an exception with a specific message.
   *
   * @param message - error message
   * @param request - module request the job belongs to
   * @param jobId - tenant job identifier
   * @param cause - the failure that stopped polling
   */
  public TenantJobPollingException(String message, ModuleRequest request, String jobId, Throwable cause) {
    super(message, cause, request, jobId);
  }
}
