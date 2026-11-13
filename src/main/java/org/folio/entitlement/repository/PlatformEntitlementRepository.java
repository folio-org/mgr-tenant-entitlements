package org.folio.entitlement.repository;

import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformEntitlementRepository extends JpaRepository<PlatformEntitlementEntity, String> {
}
