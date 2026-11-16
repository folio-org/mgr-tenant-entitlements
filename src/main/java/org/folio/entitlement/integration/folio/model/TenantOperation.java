package org.folio.entitlement.integration.folio.model;

import static java.util.Objects.requireNonNull;

/**
 * Outcome of a tenant API call: finished by the call itself, or accepted as a background job to be polled.
 */
public record TenantOperation(String jobId) {

  /**
   * Creates an operation that the module finished within the call, so there is nothing to wait for.
   *
   * @return operation without a job
   */
  public static TenantOperation completed() {
    return new TenantOperation(null);
  }

  /**
   * Creates an operation that the module accepted as a background job.
   *
   * @param jobId - identifier of the tenant job, must not be null
   * @return operation to be waited for
   */
  public static TenantOperation accepted(String jobId) {
    return new TenantOperation(requireNonNull(jobId, "jobId must not be null"));
  }

  /**
   * Tells whether the module continues the operation in the background.
   *
   * @return {@code true} if there is a tenant job to wait for
   */
  public boolean isAsync() {
    return jobId != null;
  }
}
