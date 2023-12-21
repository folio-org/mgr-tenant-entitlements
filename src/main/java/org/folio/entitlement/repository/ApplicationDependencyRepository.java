package org.folio.entitlement.repository;

import java.util.List;
import java.util.UUID;
import org.folio.entitlement.domain.entity.ApplicationDependencyEntity;
import org.folio.entitlement.domain.entity.key.ApplicationDependencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationDependencyRepository
  extends JpaRepository<ApplicationDependencyEntity, ApplicationDependencyKey> {

  List<ApplicationDependencyEntity> findByTenantIdAndParentNameIn(UUID tenantId, List<String> parentNames);

  @Query(nativeQuery = true, value = """
    WITH RECURSIVE dependencies AS (
      SELECT base.*
        FROM {h-schema}application_dependency base
        WHERE base.parent_name in :parent_names
          AND base.tenant_id = :tenant_id
      UNION
      SELECT
        FROM {h-schema}application_dependency ad
        INNER JOIN dependencies d ON d.application_name = ad.parent_name
          AND d.tenant_id = ad.tenant_id
    ) SELECT *
        FROM dependencies""")
  List<ApplicationDependencyEntity> findAllByTenantIdAndParentNameIn(@Param("tenant_id") UUID tenantId,
    @Param("parent_names") List<String> parentNames);
}
