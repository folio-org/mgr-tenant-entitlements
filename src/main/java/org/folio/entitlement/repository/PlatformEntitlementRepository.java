package org.folio.entitlement.repository;

import java.util.List;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformEntitlementRepository extends JpaRepository<PlatformEntitlementEntity, String> {

  List<PlatformEntitlementEntity> findByApplicationNameIn(List<String> names);
}
