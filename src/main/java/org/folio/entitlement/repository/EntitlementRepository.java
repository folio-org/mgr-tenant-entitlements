package org.folio.entitlement.repository;

import java.util.List;
import java.util.UUID;
import org.folio.entitlement.domain.entity.EntitlementEntity;
import org.folio.entitlement.domain.entity.key.EntitlementKey;
import org.folio.spring.cql.JpaCqlRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EntitlementRepository extends JpaCqlRepository<EntitlementEntity, EntitlementKey> {

  List<EntitlementEntity> findByTenantIdAndApplicationIdIn(UUID tenantId, List<String> applicationIds);

  List<EntitlementEntity> findByTenantIdAndApplicationNameIn(UUID tenantId, List<String> applicationNames);

  List<EntitlementEntity> findByTenantId(UUID tenantId);
}
