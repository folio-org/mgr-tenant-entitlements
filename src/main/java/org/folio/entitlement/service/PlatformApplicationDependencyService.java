package org.folio.entitlement.service;

import static java.util.stream.Collectors.toSet;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.common.domain.model.Dependency;
import org.folio.entitlement.domain.entity.PlatformApplicationDependencyEntity;
import org.folio.entitlement.mapper.PlatformApplicationDependencyMapper;
import org.folio.entitlement.repository.PlatformApplicationDependencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PlatformApplicationDependencyService {

  private final PlatformApplicationDependencyMapper mapper;
  private final PlatformApplicationDependencyRepository repository;

  /**
   * Saves dependencies of a platform application. Saving the same dependencies again changes nothing.
   *
   * @param applicationId - application identifier
   * @param dependencies - application dependencies
   */
  public void saveDependencies(String applicationId, List<Dependency> dependencies) {
    if (CollectionUtils.isEmpty(dependencies)) {
      return;
    }

    repository.saveAll(toEntities(applicationId, dependencies));
  }

  public void deleteDependencies(String applicationId, List<Dependency> dependencies) {
    if (CollectionUtils.isEmpty(dependencies)) {
      return;
    }

    repository.deleteAllInBatch(toEntities(applicationId, dependencies));
  }

  private Set<PlatformApplicationDependencyEntity> toEntities(String applicationId, List<Dependency> dependencies) {
    return dependencies.stream()
      .map(dependency -> mapper.map(applicationId, dependency))
      .collect(toSet());
  }
}
