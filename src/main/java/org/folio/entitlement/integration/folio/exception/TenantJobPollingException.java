package org.folio.entitlement.integration.folio.exception;

import static java.lang.String.format;

import java.io.Serial;
import org.folio.entitlement.integration.folio.model.ModuleRequest;

/**
 * The tenant job cannot be read, or polling was interrupted.
 */
public class TenantJobPollingException extends TenantJobException {

  @Serial private static final long serialVersionUID = 1L;

  public TenantJobPollingException(ModuleRequest request, String jobId, Throwable cause) {
    this(format("Failed to poll tenant job %s of module %s", jobId, request.getModuleId()), request, jobId, cause);
  }

  public TenantJobPollingException(String message, ModuleRequest request, String jobId, Throwable cause) {
    super(message, cause, request, jobId);
  }
}
