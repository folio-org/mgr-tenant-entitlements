package org.folio.entitlement.domain.entity;

import io.hypersistence.utils.hibernate.type.basic.PostgreSQLEnumType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.time.ZonedDateTime;
import java.util.UUID;
import lombok.Data;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.utils.SemverUtils;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

@Data
@Entity
@Table(name = "entitlement_flow")
public class EntitlementFlowEntity {

  /**
   * An entity identifier.
   */
  @Id
  @Column(name = "entitlement_flow_id")
  private UUID entitlementFlowId;

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
   * An entitlement request type for tenant.
   */
  @Type(PostgreSQLEnumType.class)
  @Enumerated(EnumType.STRING)
  @Column(name = "type", columnDefinition = "entitlement_flow_type")
  private EntitlementType type;

  /**
   * An entitlement execution status (can change through granting entitlement for tenant).
   */
  @Type(PostgreSQLEnumType.class)
  @Enumerated(EnumType.STRING)
  @Column(name = "status", columnDefinition = "entitlement_flow_status_type")
  private ExecutionStatus status;

  /**
   * An entitlement startup timestamp.
   */
  @CreationTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "started_at", updatable = false)
  private ZonedDateTime startedAt;

  /**
   * An entitlement finishing timestamp.
   */
  @UpdateTimestamp
  @Column(name = "finished_at")
  @Temporal(TemporalType.TIMESTAMP)
  private ZonedDateTime finishedAt;

  /**
   * Sets application name and version from application id.
   */
  @PrePersist
  private void calculateNameVersion() {
    applicationName = SemverUtils.getName(applicationId);
    applicationVersion = SemverUtils.getVersion(applicationId);
  }
}
