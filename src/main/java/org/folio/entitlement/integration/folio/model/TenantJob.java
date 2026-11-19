package org.folio.entitlement.integration.folio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

/**
 * Tenant job returned by a module's {@code GET /_/tenant/{id}}: its state, and the error and messages when the module
 * failed to initialize the tenant. Fields that MTE does not use are ignored.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TenantJob {

  private String id;
  private String tenant;
  private Boolean complete;
  private String error;
  private List<String> messages;
}
