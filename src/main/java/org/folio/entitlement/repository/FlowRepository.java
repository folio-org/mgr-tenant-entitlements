package org.folio.entitlement.repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.folio.entitlement.domain.entity.FlowEntity;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FlowRepository extends AbstractFlowRepository<FlowEntity> {

  @Query("SELECT e.status FROM FlowEntity e WHERE e.id = :flowId")
  Optional<EntityExecutionStatus> findStatusById(@Param("flowId") UUID flowId);

  @Override
  @Query("""
    SELECT
      EXISTS (SELECT 1 FROM FlowStageEntity fs WHERE fs.flowId = :flowId AND fs.status = :status)
      OR EXISTS (SELECT 1 FROM ApplicationFlowEntity af WHERE af.flowId = :flowId AND af.status = :status)
    FROM FlowEntity f
    WHERE f.id = :flowId""")
  boolean existsAnyStageByFlowIdAndStatus(@Param("flowId") UUID flowId, @Param("status") EntityExecutionStatus status);

  @Modifying
  @Query("""
    UPDATE FlowEntity f
    SET f.status = :status, f.finishedAt = :finishedAt
    WHERE f.id = :flowId AND f.status IN :currentStatuses
      AND NOT EXISTS (
          SELECT 1 FROM FlowStageEntity s
          WHERE s.flowId = f.id AND s.status IN :currentStatuses)
      AND NOT EXISTS (
          SELECT 1 FROM ApplicationFlowEntity af
          WHERE af.flowId = f.id AND af.status IN :currentStatuses)""")
  int updateStatusByIdIfCurrentInAndNoStagesWithStatus(@Param("flowId") UUID flowId,
    @Param("status") EntityExecutionStatus status,
    @Param("currentStatuses") Collection<EntityExecutionStatus> currentStatuses,
    @Param("finishedAt") ZonedDateTime finishedAt);
}
