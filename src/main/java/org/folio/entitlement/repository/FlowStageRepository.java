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

  /**
   * Counts the flow's stages per status, for stages in one of the given statuses whose name ends with one of the
   * given suffixes. A status that no stage has is left out.
   *
   * <p>A stage name is {@code <moduleId>-<class name>}. The module id contains dashes and the class name does not,
   * so the suffix is the text from the last dash on. The suffixes are compared as whole values, not as patterns.</p>
   */
  @Query("""
    SELECT e.status AS status, count(e) AS total FROM FlowStageEntity e
    WHERE e.flowId = :flowId AND e.status IN :statuses
      AND substring(e.stageName, length(e.stageName) - locate('-', reverse(e.stageName)) + 1) IN :suffixes
    GROUP BY e.status""")
  List<StatusCount> countByStatusesAndStageNameSuffixes(@Param("flowId") UUID flowId,
    @Param("statuses") Collection<EntityExecutionStatus> statuses, @Param("suffixes") Collection<String> suffixes);

  /**
   * Finds the flow's stages in one of the given statuses whose name ends with one of the given suffixes, ordered by
   * name. See {@link #countByStatusesAndStageNameSuffixes} for how the suffix is read.
   */
  @Query("""
    SELECT e FROM FlowStageEntity e
    WHERE e.flowId = :flowId AND e.status IN :statuses
      AND substring(e.stageName, length(e.stageName) - locate('-', reverse(e.stageName)) + 1) IN :suffixes
    ORDER BY e.stageName""")
  List<FlowStageEntity> findByStatusesAndStageNameSuffixes(@Param("flowId") UUID flowId,
    @Param("statuses") Collection<EntityExecutionStatus> statuses, @Param("suffixes") Collection<String> suffixes);

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
  @Query("""
    UPDATE FlowStageEntity e
    SET e.status = :status, e.finishedAt = :finishedAt
    WHERE e.status IN :currentStatuses
      AND (e.flowId = :flowId
          OR e.flowId IN (SELECT af.id FROM ApplicationFlowEntity af WHERE af.flowId = :flowId))""")
  int updateStatusByFlowIdIfCurrentIn(@Param("flowId") UUID flowId,
    @Param("status") EntityExecutionStatus status,
    @Param("currentStatuses") Collection<EntityExecutionStatus> currentStatuses,
    @Param("finishedAt") ZonedDateTime finishedAt);

  @Modifying
  @Query("""
    UPDATE FlowStageEntity e
    SET e.status = :status, e.finishedAt = :finishedAt
    WHERE e.id = :stageId AND e.status IN :currentStatuses""")
  int updateStatusByStageIdIfCurrentIn(@Param("stageId") UUID stageId,
    @Param("status") EntityExecutionStatus status,
    @Param("currentStatuses") Collection<EntityExecutionStatus> currentStatuses,
    @Param("finishedAt") ZonedDateTime finishedAt);

  /**
   * Compare-and-set that fails a stage and records why. {@code errorType} is bound rather than hardcoded so an
   * async failure can be told apart from a locally thrown one, and the status is bound as a parameter for
   * consistency with the sibling statements above.
   */
  @Modifying
  @Query("""
    UPDATE FlowStageEntity e
    SET e.status = :status, e.errorType = :errorType, e.errorMessage = :errDetails, e.finishedAt = :finishedAt
    WHERE e.id = :stageId AND e.status IN :currentStatuses""")
  int markFailedByStageIdIfCurrentIn(@Param("stageId") UUID stageId,
    @Param("status") EntityExecutionStatus status,
    @Param("errorType") String errorType,
    @Param("errDetails") String errDetails,
    @Param("currentStatuses") Collection<EntityExecutionStatus> currentStatuses,
    @Param("finishedAt") ZonedDateTime finishedAt);

  interface StatusCount {

    EntityExecutionStatus getStatus();

    long getTotal();
  }
}
