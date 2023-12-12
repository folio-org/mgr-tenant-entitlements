package org.folio.entitlement.repository;

import java.util.List;
import java.util.UUID;
import org.folio.entitlement.domain.entity.EntitlementStageEntity;
import org.folio.entitlement.domain.entity.key.EntitlementStageKey;
import org.folio.spring.cql.JpaCqlRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EntitlementStageRepository extends JpaCqlRepository<EntitlementStageEntity, EntitlementStageKey> {

  @Query("""
    select entity from EntitlementStageEntity entity
    where entity.applicationFlowId = :application_flow_id
    order by entity.startedAt asc""")
  List<EntitlementStageEntity> findByApplicationFlowId(@Param("application_flow_id") UUID applicationFlowId);

  @Query(nativeQuery = true, value = """
    select es.* from {h-schema}entitlement_stage es
      join {h-schema}entitlement_flow ef on ef.entitlement_flow_id = es.entitlement_flow_id
    where ef.flow_id = :flow_id
    order by es.started_at""")
  List<EntitlementStageEntity> findByFlowId(@Param("flow_id") UUID flowId);
}
