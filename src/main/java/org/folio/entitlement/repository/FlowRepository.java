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
}
