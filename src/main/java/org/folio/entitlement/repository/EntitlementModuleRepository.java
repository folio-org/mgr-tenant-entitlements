package org.folio.entitlement.repository;

import java.util.List;
import java.util.UUID;
import org.folio.entitlement.domain.entity.key.EntitlementModuleEntity;
import org.folio.entitlement.domain.entity.key.EntitlementModuleKey;
import org.folio.spring.cql.JpaCqlRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface EntitlementModuleRepository extends JpaCqlRepository<EntitlementModuleEntity, EntitlementModuleKey> {

  Page<EntitlementModuleEntity> findAllByModuleId(String moduleId, Pageable pageable);

  List<EntitlementModuleEntity> findAllByModuleIdAndTenantId(String moduleId, UUID tenantId);
}
