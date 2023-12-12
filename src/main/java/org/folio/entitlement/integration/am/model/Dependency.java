package org.folio.entitlement.integration.am.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.entitlement.domain.model.WithNameVersion;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class Dependency implements WithNameVersion {

  private String name;
  private String version;
}
