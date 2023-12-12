package org.folio.entitlement.integration.tm.model;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class Tenant {

  private UUID id;
  private String name;
  private String description;
}
