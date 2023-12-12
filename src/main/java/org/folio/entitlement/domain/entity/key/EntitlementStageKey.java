package org.folio.entitlement.domain.entity.key;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Embeddable
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class EntitlementStageKey implements Serializable {

  @Serial private static final long serialVersionUID = -6878847328039891893L;

  /**
   * An application entitlement flow identifier (reference to the entitlement_flow table).
   */
  @Column(name = "entitlement_flow_id")
  private UUID applicationFlowId;

  /**
   * Stage name, assuming that each stage will have unique name.
   */
  @Column(name = "stage")
  private String name;
}
