package org.folio.entitlement.domain.entity;

import static jakarta.persistence.EnumType.STRING;

import io.hypersistence.utils.hibernate.type.basic.PostgreSQLEnumType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.time.ZonedDateTime;
import java.util.UUID;
import lombok.Data;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.entity.key.EntitlementStageKey;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

@Data
@Entity
@IdClass(EntitlementStageKey.class)
@Table(name = "entitlement_stage")
public class EntitlementStageEntity {

  /**
   * A stage name.
   */
  @Id
  @Column(name = "stage")
  private String name;

  /**
   * A back reference to the application entitlement flow.
   */
  @Id
  @Column(name = "entitlement_flow_id")
  private UUID applicationFlowId;

  /**
   * A stage execution status as {@link String} value.
   */
  @Enumerated(STRING)
  @Type(PostgreSQLEnumType.class)
  @Column(name = "status", columnDefinition = "entitlement_stage_status_type")
  private ExecutionStatus status;

  /**
   * An error type, nullable.
   */

  @Column(name = "error_type")
  private String errorType;

  /**
   * An error message, nullable.
   */
  @Column(name = "error_message")
  private String errorMessage;

  /**
   * Timestamp when a stage is started.
   */
  @CreationTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "started_at", updatable = false)
  private ZonedDateTime startedAt;

  /**
   * A timestamp when a stage is finished.
   */
  @UpdateTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "finished_at")
  private ZonedDateTime finishedAt;
}
