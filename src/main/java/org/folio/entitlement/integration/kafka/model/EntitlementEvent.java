package org.folio.entitlement.integration.kafka.model;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EntitlementEvent {

  private String type;
  private String moduleId;
  private String tenantName;
  private UUID tenantId;
  private String applicationId;

  public EntitlementEvent(String type, String moduleId, String tenantName, UUID tenantId) {
    this(type, moduleId, tenantName, tenantId, null);
  }
}
