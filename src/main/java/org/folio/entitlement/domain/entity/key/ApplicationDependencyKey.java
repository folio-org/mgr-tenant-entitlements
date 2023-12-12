package org.folio.entitlement.domain.entity.key;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class ApplicationDependencyKey implements Serializable {

  @Serial private static final long serialVersionUID = -3156220848236881778L;

  private UUID tenantId;
  private String applicationId;
  private String parentName;
  private String parentVersion;
}
