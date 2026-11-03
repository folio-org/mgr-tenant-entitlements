package org.folio.entitlement.integration.folio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Async Tenant API job (RMB), returned by {@code GET /_/tenant/{id}}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TenantJob {

  private String id;

  /**
   * Whether the job has finished (successfully or with an error).
   */
  private boolean complete;

  /**
   * Error message, set when the job failed.
   */
  private String error;
}
