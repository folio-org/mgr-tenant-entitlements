package org.folio.entitlement.integration.kafka.model;

import lombok.Data;

@Data
public class PermissionMappingValue {
  private String endpoint;
  private String method;
}
