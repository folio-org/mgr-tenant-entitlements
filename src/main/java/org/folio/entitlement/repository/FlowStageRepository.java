package org.folio.entitlement.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.folio.entitlement.domain.entity.FlowStageEntity;
import org.folio.entitlement.domain.entity.key.FlowStageKey;
import org.folio.spring.cql.JpaCqlRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FlowStageRepository extends JpaCqlRepository<FlowStageEntity, FlowStageKey> {

  @Query("""
    select entity from FlowStageEntity entity
    where entity.flowId = :flowId
    order by entity.startedAt asc""")
  List<FlowStageEntity> findByFlowId(@Param("flowId") UUID flowId);

  @Query(value = """
    select entity from FlowStageEntity entity
    where entity.flowId in :flowIds
    order by entity.startedAt asc""")
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
}
