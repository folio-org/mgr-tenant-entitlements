package org.folio.entitlement.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.folio.entitlement.domain.entity.ApplicationFlowEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationFlowRepository extends AbstractFlowRepository<ApplicationFlowEntity> {

  @Query("SELECT e FROM ApplicationFlowEntity e WHERE e.flowId = :flowId ORDER BY e.startedAt ASC")
  List<ApplicationFlowEntity> findByFlowId(UUID flowId);

  @Query("SELECT e FROM ApplicationFlowEntity e WHERE e.flowId in :flowIds ORDER BY e.startedAt")
  List<ApplicationFlowEntity> findByFlowIds(@Param("flowIds") List<UUID> flowIds);

  @Query(nativeQuery = true, value = """
    SELECT DISTINCT af.* FROM {h-schema}application_flow af
    INNER JOIN (
      SELECT MAX(finished_at) AS finished_at, tenant_id, application_id
      FROM {h-schema}application_flow af
      WHERE af.tenant_id = :tenant_id
        AND af.application_id IN :application_ids
      GROUP BY tenant_id, application_id
    ) laf ON af.finished_at = laf.finished_at
          AND af.tenant_id = laf.tenant_id
          AND af.application_id = laf.application_id""")
  List<ApplicationFlowEntity> findLastFlows(
    @Param("application_ids") Collection<String> applicationIds, @Param("tenant_id") UUID tenantId);

  @Query(nativeQuery = true, value = """
    SELECT DISTINCT af.* FROM {h-schema}application_flow af
    INNER JOIN (
      SELECT MAX(finished_at) AS finished_at, tenant_id, application_name
      FROM {h-schema}application_flow af
      WHERE af.tenant_id = :tenant_id
        AND af.application_name IN :application_names
      GROUP BY tenant_id, application_name
    ) laf ON af.finished_at = laf.finished_at
          AND af.tenant_id = laf.tenant_id
          AND af.application_name = laf.application_name""")
  List<ApplicationFlowEntity> findLastFlowsByApplicationNames(
    @Param("application_names") Collection<String> applicationNames, @Param("tenant_id") UUID tenantId);

  @Modifying
  @Query("DELETE ApplicationFlowEntity entity WHERE entity.flowId = :flowId and entity.status = 'QUEUED'")
  void removeQueuedFlows(@Param("flowId") UUID flowId);
}
