package org.folio.entitlement.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.ZonedDateTime;
import java.util.UUID;
import lombok.Data;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Data
@MappedSuperclass
public class AbstractFlowEntity {

  /**
   * An entity identifier.
   */
  @Id
  @Column(name = "flow_id")
  private UUID id;

  /**
   * An entitlement execution status (can change through granting entitlement for tenant).
   */
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "status", columnDefinition = "entitlement_flow_status_type")
  private EntityExecutionStatus status;

  /**
   * An entitlement startup timestamp.
   */
  @CreationTimestamp
  @Column(name = "started_at", updatable = false)
  private ZonedDateTime startedAt;

  /**
   * An entitlement finishing timestamp.
   */
  @UpdateTimestamp
  @Column(name = "finished_at")
  private ZonedDateTime finishedAt;

  /**
   * When the flow finished all of its synchronous work and started waiting for asynchronous stage confirmations,
   * {@code null} when the flow is not waiting on anything.
   *
   * <p>Written once by the flow finalizer and never rewritten. This is deliberately not {@code finishedAt}: that
   * column is touched by {@link UpdateTimestamp} on every row update, so it cannot be used to measure how long a
   * flow has been waiting. Two things depend on this distinction - a flow cannot be completed by an inbound
   * result until the anchor is set (which is what prevents a fast result from finishing a flow that still has
   * unstarted stages), and the staleness sweeper measures its timeout from it.</p>
   */
  @Column(name = "awaiting_async_since")
  private ZonedDateTime awaitingAsyncSince;
}
