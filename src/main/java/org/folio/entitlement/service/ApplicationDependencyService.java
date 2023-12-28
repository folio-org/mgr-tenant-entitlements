package org.folio.entitlement.service;

import static java.util.stream.Collectors.toSet;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.entitlement.domain.entity.ApplicationDependencyEntity;
import org.folio.entitlement.integration.am.model.Dependency;
import org.folio.entitlement.mapper.ApplicationDependencyMapper;
import org.folio.entitlement.repository.ApplicationDependencyRepository;
import org.folio.entitlement.utils.SemverUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ApplicationDependencyService {

  private final ApplicationDependencyMapper mapper;
  private final ApplicationDependencyRepository repository;

  @Transactional(readOnly = true)
  public List<ApplicationDependencyEntity> findByParentApplicationId(UUID tenantId, String parentApplicationId) {
    var parentName = SemverUtils.getName(parentApplicationId);
    var parentVersion = SemverUtils.getVersion(parentApplicationId);

    return repository.findByTenantIdAndParentNameIn(tenantId, List.of(parentName)).stream()
      .filter(satisfiesVersion(parentVersion))
      .toList();
  }

  @Transactional(readOnly = true)
  public List<ApplicationDependencyEntity> findAllByParentApplicationName(UUID tenantId, String parentApplicationId) {
    var parentName = SemverUtils.getName(parentApplicationId);

    return repository.findAllByTenantIdAndParentNameIn(tenantId, List.of(parentName));
  }

  /**
   * Saves application dependencies for entitlement.
   *
   * @param tenantId - tenant identifier as {@link UUID}
   * @param applicationId - application identifier as {@link String}
   * @param dependencies - list of application dependencies
   * @throws jakarta.persistence.EntityNotFoundException if entitlement entity is not found by id
   */
  public void saveEntitlementDependencies(UUID tenantId, String applicationId, List<Dependency> dependencies) {
    if (CollectionUtils.isEmpty(dependencies)) {
      return;
    }

    var entities = toEntities(tenantId, applicationId, dependencies);
    repository.saveAll(entities);
  }

  public void deleteEntitlementDependencies(UUID tenantId, String applicationId, List<Dependency> dependencies) {
    if (CollectionUtils.isEmpty(dependencies)) {
      return;
    }

    var entities = toEntities(tenantId, applicationId, dependencies);
    repository.deleteAllInBatch(entities);
  }

  private Set<ApplicationDependencyEntity> toEntities(UUID tenantId, String applicationId,
    List<Dependency> dependencies) {
    return dependencies.stream()
      .map(dependency -> mapper.map(tenantId, applicationId, dependency))
      .collect(toSet());
  }

  private static Predicate<ApplicationDependencyEntity> satisfiesVersion(String parentVersion) {
    return dependency -> SemverUtils.satisfies(parentVersion, dependency.getParentVersion());
  }
}
