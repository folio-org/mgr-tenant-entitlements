package org.folio.entitlement.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "flow")
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class FlowEntity extends AbstractFlowEntity {

  /**
   * A reference id to the entity in entitlement table.
   */
  @Column(name = "tenant_id")
  private UUID tenantId;
}
