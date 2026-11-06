package org.folio.entitlement.repository;

import java.util.List;
import org.folio.entitlement.domain.entity.PlatformApplicationDependencyEntity;
import org.folio.entitlement.domain.entity.key.PlatformApplicationDependencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformApplicationDependencyRepository
  extends JpaRepository<PlatformApplicationDependencyEntity, PlatformApplicationDependencyKey> {

  /**
   * Finds platform applications that depend on the given parent applications.
   */
  List<PlatformApplicationDependencyEntity> findByParentNameIn(List<String> parentNames);
}
