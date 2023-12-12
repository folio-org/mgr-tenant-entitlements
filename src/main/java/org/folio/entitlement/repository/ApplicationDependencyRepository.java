package org.folio.entitlement.repository;

import java.util.List;
import java.util.UUID;
import org.folio.entitlement.domain.entity.ApplicationDependencyEntity;
import org.folio.entitlement.domain.entity.key.ApplicationDependencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationDependencyRepository
  extends JpaRepository<ApplicationDependencyEntity, ApplicationDependencyKey> {

  List<ApplicationDependencyEntity> findByTenantIdAndParentNameIn(UUID tenantId, List<String> parentNames);
}
