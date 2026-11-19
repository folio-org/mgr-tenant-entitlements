package org.folio.entitlement.domain.entity.key;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class PlatformApplicationDependencyKey implements Serializable {

  @Serial private static final long serialVersionUID = 5826391047251385202L;

  private String applicationId;
  private String parentName;
  private String parentVersion;
}
