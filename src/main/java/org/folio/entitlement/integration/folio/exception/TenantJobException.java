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

  protected TenantJobException(String message, ModuleRequest request, String jobId, Parameter... extra) {
    super(message, parameters(request, jobId, extra));
  }

  protected TenantJobException(String message, Throwable cause, ModuleRequest request, String jobId) {
    super(message, parameters(request, jobId), cause);
  }

  private static List<Parameter> parameters(ModuleRequest request, String jobId, Parameter... extra) {
    var result = new ArrayList<Parameter>();
    result.add(new Parameter().key("moduleId").value(request.getModuleId()));
    result.add(new Parameter().key("tenant").value(request.getTenantName()));
    result.add(new Parameter().key("jobId").value(jobId));
    Collections.addAll(result, extra);
    return result;
  }
}
