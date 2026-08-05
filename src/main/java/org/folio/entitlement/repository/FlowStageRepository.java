package org.folio.entitlement.repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.folio.entitlement.domain.entity.FlowStageEntity;
import org.folio.entitlement.domain.entity.key.FlowStageKey;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.spring.cql.JpaCqlRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FlowStageRepository extends JpaCqlRepository<FlowStageEntity, FlowStageKey> {

  @Query("SELECT entity from FlowStageEntity entity WHERE entity.id = :stageId")
  Optional<FlowStageEntity> findByStageId(@Param("stageId") UUID stageId);

  @Query("""
    SELECT entity from FlowStageEntity entity
    WHERE entity.flowId = :flowId
    ORDER BY entity.startedAt asc""")
  List<FlowStageEntity> findByFlowId(@Param("flowId") UUID flowId);

  @Query(value = """
    SELECT entity from FlowStageEntity entity
    WHERE entity.flowId in :flowIds
    ORDER BY entity.startedAt asc""")
  List<FlowStageEntity> findByFlowIds(@Param("flowIds") Collection<UUID> flowIds);

  @Query(nativeQuery = true, value = """
    SELECT fs.* FROM {h-schema}flow_stage fs
    WHERE fs.flow_id = :flow_id
      AND fs.error_message IS NOT NULL
    UNION SELECT fs.* FROM {h-schema}application_flow af
      JOIN {h-schema}flow_stage fs ON fs.flow_id = af.application_flow_id
        WHERE af.flow_id = :flow_id
        AND fs.error_message IS NOT NULL
    ORDER BY finished_at""")
  List<FlowStageEntity> findLastFailedStage(@Param("flow_id") UUID flowId);

  /**
   * Compare-and-set on the stages of the flow and of its application flows. Error fields are left untouched so a
   * later status overwrite by the still-running stage does not keep stale error text. {@code finishedAt} is passed
   * in because a bulk update bypasses {@link org.hibernate.annotations.UpdateTimestamp}.
   */
  @Modifying
  @Query("UPDATE FlowStageEntity e SET e.status = :status, e.finishedAt = :finishedAt "
    + "WHERE e.status IN :currentStatuses AND (e.flowId = :flowId "
    + "OR e.flowId IN (SELECT af.id FROM ApplicationFlowEntity af WHERE af.flowId = :flowId))")
  int updateStatusByFlowIdIfCurrentIn(@Param("flowId") UUID flowId,
    @Param("status") EntityExecutionStatus status,
    @Param("currentStatuses") Collection<EntityExecutionStatus> currentStatuses,
    @Param("finishedAt") ZonedDateTime finishedAt);
}
