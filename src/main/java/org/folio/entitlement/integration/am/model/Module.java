package org.folio.entitlement.integration.am.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.entitlement.domain.model.Artifact;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class Module implements Artifact {

  private String id;
  private String name;
  private String version;
}
