package org.folio.entitlement.repository;

import java.util.Optional;
import java.util.UUID;
import org.folio.entitlement.domain.entity.FlowEntity;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
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
}
