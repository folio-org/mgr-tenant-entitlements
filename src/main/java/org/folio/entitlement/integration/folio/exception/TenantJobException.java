package org.folio.entitlement.integration.folio.exception;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.folio.model.ModuleRequest;

/**
 * Failure of an asynchronous tenant job. Never carries an HTTP status, so the stage-level retry does not repeat the
 * tenant API call.
 */
public abstract class TenantJobException extends IntegrationException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates an exception that carries the module, tenant and job in its error parameters.
   *
   * @param message - error message
   * @param request - module request the job belongs to
   * @param jobId - tenant job identifier
   * @param extra - additional error parameters
   */
  protected TenantJobException(String message, ModuleRequest request, String jobId, Parameter... extra) {
    super(message, parameters(request, jobId, extra));
  }

  /**
   * Creates an exception with a cause that carries the module, tenant and job in its error parameters.
   *
   * @param message - error message
   * @param cause - error cause
   * @param request - module request the job belongs to
   * @param jobId - tenant job identifier
   */
  protected TenantJobException(String message, Throwable cause, ModuleRequest request, String jobId) {
    super(message, parameters(request, jobId), cause);
  }

  /**
   * Builds the error parameters shared by all tenant job failures.
   *
   * @param request - module request the job belongs to
   * @param jobId - tenant job identifier
   * @param extra - additional error parameters
   * @return parameters {@code moduleId}, {@code tenant}, {@code jobId} followed by the extra ones
   */
  private static List<Parameter> parameters(ModuleRequest request, String jobId, Parameter... extra) {
    var result = new ArrayList<Parameter>();
    result.add(new Parameter().key("moduleId").value(request.getModuleId()));
    result.add(new Parameter().key("tenant").value(request.getTenantName()));
    result.add(new Parameter().key("jobId").value(jobId));
    Collections.addAll(result, extra);
    return result;
  }
}
