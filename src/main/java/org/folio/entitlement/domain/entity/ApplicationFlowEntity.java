package org.folio.entitlement.domain.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.folio.entitlement.utils.SemverUtils;

@Data
@Entity
@ToString(callSuper = true)
@Table(name = "application_flow")
@EqualsAndHashCode(callSuper = true)
@AttributeOverride(name = "id", column = @Column(name = "application_flow_id"))
public class ApplicationFlowEntity extends AbstractFlowEntity {

  /**
   * A reference id to the entity in entitlement table.
   */
  @Column(name = "tenant_id")
  private UUID tenantId;

  /**
   * A reference id to the entity in entitlement table.
   */
  @Column(name = "application_id")
  private String applicationId;

  /**
   * Application's name that is a part of id.
   */
  @Column(name = "application_name", updatable = false)
  private String applicationName;

  /**
   * Application's version that is a part of id.
   */
  @Column(name = "application_version", updatable = false)
  private String applicationVersion;

  /**
   * A flow identifier, which is used to grant entitlement for tenant.
   */
  @Column(name = "flow_id")
  private UUID flowId;

  /**
   * Sets application name and version from application id.
   */
  @PrePersist
  private void calculateNameVersion() {
    applicationName = SemverUtils.getName(applicationId);
    applicationVersion = SemverUtils.getVersion(applicationId);
  }
}
