package org.folio.entitlement.domain.entity;

import static jakarta.persistence.EnumType.STRING;

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
import org.folio.entitlement.domain.entity.key.FlowStageKey;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@IdClass(FlowStageKey.class)
@Table(name = "flow_stage")
public class FlowStageEntity {

  /**
   * A stage name.
   */
  @Id
  @Column(name = "stage")
  private String stageName;

  /**
   * A back reference to the application entitlement flow.
   */
  @Id
  @Column(name = "flow_id")
  private UUID flowId;

  /**
   * A stage execution status as {@link String} value.
   */
  @Enumerated(STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "status", columnDefinition = "entitlement_stage_status_type")
  private EntityExecutionStatus status;

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
