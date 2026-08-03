package org.folio.entitlement.repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.UUID;
import org.folio.entitlement.domain.entity.FlowEntity;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FlowRepository extends AbstractFlowRepository<FlowEntity> {

  /**
   * Compare-and-set on the flow status: the status check is a part of the statement, so a status set concurrently by
   * a finalizer stage cannot be overwritten. {@code finishedAt} is passed in because a bulk update bypasses
   * {@link org.hibernate.annotations.UpdateTimestamp}.
   */
  @Modifying
  @Query("UPDATE FlowEntity e SET e.status = :status, e.finishedAt = :finishedAt "
    + "WHERE e.id = :flowId AND e.status IN :currentStatuses")
  int updateStatusIfCurrentIn(@Param("flowId") UUID flowId,
    @Param("status") EntityExecutionStatus status,
    @Param("currentStatuses") Collection<EntityExecutionStatus> currentStatuses,
    @Param("finishedAt") ZonedDateTime finishedAt);
}
