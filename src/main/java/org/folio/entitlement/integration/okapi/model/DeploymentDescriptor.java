package org.folio.entitlement.integration.okapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class DeploymentDescriptor {

  private String srvcId;
  private String instId;
  private String nodeId;
  private String url;
}
