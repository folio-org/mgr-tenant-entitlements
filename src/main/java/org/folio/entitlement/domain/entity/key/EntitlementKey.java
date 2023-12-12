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
public class EntitlementKey implements Serializable {

  @Serial private static final long serialVersionUID = -7515474180397351834L;

  /**
   * Tenant id of installed application.
   */
  private UUID tenantId;

  /**
   * Reference to application descriptor.
   */
  private String applicationId;
}
