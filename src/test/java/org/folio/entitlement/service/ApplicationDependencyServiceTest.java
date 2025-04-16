package org.folio.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.applicationDependency;
import static org.folio.entitlement.support.TestValues.applicationDependencyEntity;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.folio.common.domain.model.Dependency;
import org.folio.entitlement.mapper.ApplicationDependencyMapper;
import org.folio.entitlement.repository.ApplicationDependencyRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationDependencyServiceTest {

  @InjectMocks private ApplicationDependencyService service;
  @Mock private ApplicationDependencyRepository repository;
  @Mock private ApplicationDependencyMapper mapper;

  @Test
  void findByParentApplicationId_positive() {
    var dependency = applicationDependency("test-app", "0.0.1");
    var dependencyEntity = applicationDependencyEntity(dependency);

    when(repository.findByTenantIdAndParentNameIn(TENANT_ID, List.of("test-app")))
      .thenReturn(List.of(dependencyEntity));

    var actual = service.findByParentApplicationId(TENANT_ID, "test-app-0.0.1");

    assertThat(actual).isEqualTo(List.of(dependencyEntity));
  }

  @Test
  void findByParentApplicationId_positive_versionMismatch() {
    var dependency = applicationDependency("test-app", "1.x");
    var dependencyEntity = applicationDependencyEntity(dependency);

    when(repository.findByTenantIdAndParentNameIn(TENANT_ID, List.of("test-app")))
      .thenReturn(List.of(dependencyEntity));

    var actual = service.findByParentApplicationId(TENANT_ID, "test-app-0.0.1");

    assertThat(actual).isEmpty();
  }

  @Test
  void findAllByParentApplicationName_positive() {
    var dependency = applicationDependency("test-app", "0.0.1");
    var dependencyEntity = applicationDependencyEntity(dependency);

    when(repository.findAllByTenantIdAndParentNameIn(TENANT_ID, List.of("test-app")))
      .thenReturn(List.of(dependencyEntity));

    var actual = service.findAllByParentApplicationName(TENANT_ID, "test-app-0.0.1");

    assertThat(actual).isEqualTo(List.of(dependencyEntity));
  }

  @Test
  void saveEntitlementDependencies_positive() {
    var dependency = applicationDependency("test-app", "0.0.1");
    var dependencies = List.of(dependency);
    var expectedDependencyEntity = applicationDependencyEntity(dependency);

    when(mapper.map(TENANT_ID, APPLICATION_ID, dependency)).thenReturn(expectedDependencyEntity);

    service.saveEntitlementDependencies(TENANT_ID, APPLICATION_ID, dependencies);

    var expectedEntity = applicationDependencyEntity(dependency);
    verify(repository).saveAll(Set.of(expectedEntity));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void saveEntitlementDependencies_positive_emptyDependencies(List<Dependency> dependencies) {
    service.saveEntitlementDependencies(TENANT_ID, APPLICATION_ID, dependencies);
    verifyNoInteractions(repository);
  }

  @Test
  void deleteEntitlementDependencies_positive() {
    var dependency = applicationDependency("test-app", "0.0.1");
    var dependencies = List.of(dependency);
    var dependencyEntity = applicationDependencyEntity(dependency);

    when(mapper.map(TENANT_ID, APPLICATION_ID, dependency)).thenReturn(dependencyEntity);

    service.deleteEntitlementDependencies(TENANT_ID, APPLICATION_ID, dependencies);

    verify(repository).deleteAllInBatch(Set.of(dependencyEntity));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void deleteEntitlementDependencies_positive_emptyDependencies(List<Dependency> dependencies) {
    service.deleteEntitlementDependencies(TENANT_ID, APPLICATION_ID, dependencies);
    verifyNoInteractions(repository);
  }
}
