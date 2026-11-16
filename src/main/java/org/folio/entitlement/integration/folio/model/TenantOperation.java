package org.folio.entitlement.integration.folio.model;

import static java.util.Objects.requireNonNull;

/**
 * Outcome of a tenant API call: finished by the call itself, or accepted as a background job to be polled.
 */
public record TenantOperation(String jobId) {

  public static TenantOperation completed() {
    return new TenantOperation(null);
  }

  public static TenantOperation accepted(String jobId) {
    return new TenantOperation(requireNonNull(jobId, "jobId must not be null"));
  }

  public boolean isAsync() {
    return jobId != null;
  }
}
