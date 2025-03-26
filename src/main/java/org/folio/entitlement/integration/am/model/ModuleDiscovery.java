package org.folio.entitlement.integration.am.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModuleDiscovery {

  private String id;
  private String name;
  private String version;
  private String location;
}
