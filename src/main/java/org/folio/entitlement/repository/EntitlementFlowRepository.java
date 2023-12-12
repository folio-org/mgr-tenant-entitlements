package org.folio.entitlement.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.folio.entitlement.domain.entity.EntitlementFlowEntity;
import org.folio.spring.cql.JpaCqlRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EntitlementFlowRepository extends JpaCqlRepository<EntitlementFlowEntity, UUID> {

  List<EntitlementFlowEntity> findByFlowId(UUID flowId);

  @Query(nativeQuery = true, value = """
    select distinct ef.* from {h-schema}entitlement_flow ef
    inner join (
      select max(finished_at) as finished_at, tenant_id, application_id
      from {h-schema}entitlement_flow ef
      where tenant_id = :tenant_id
        and application_id in :application_ids
      group by tenant_id, application_id
    ) lef on ef.finished_at = lef.finished_at
        and ef.tenant_id = lef.tenant_id and ef.application_id = lef.application_id""")
  List<EntitlementFlowEntity> findLastEntitlementFlows(
    @Param("application_ids") List<String> applicationIds, @Param("tenant_id") UUID tenantId);

  @Query(nativeQuery = true, value = """
    select distinct ef.* from {h-schema}entitlement_flow ef
    inner join (
      select max(finished_at) as finished_at, tenant_id, application_id
      from {h-schema}entitlement_flow ef
      where tenant_id = :tenant_id
        and application_name in :application_names
      group by tenant_id, application_id
    ) lef on ef.finished_at = lef.finished_at
        and ef.tenant_id = lef.tenant_id and ef.application_id = lef.application_id""")
  List<EntitlementFlowEntity> findLastEntitlementFlowsByAppNames(
    @Param("application_names") Collection<String> applicationNames, @Param("tenant_id") UUID tenantId);
}
