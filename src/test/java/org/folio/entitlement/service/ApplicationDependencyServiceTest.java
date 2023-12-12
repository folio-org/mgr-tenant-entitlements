package org.folio.entitlement.service;

import static java.util.Collections.emptyList;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.applicationDependency;
import static org.folio.entitlement.support.TestValues.applicationDependencyEntity;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.folio.entitlement.mapper.ApplicationDependencyMapper;
import org.folio.entitlement.repository.ApplicationDependencyRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationDependencyServiceTest {

  @InjectMocks private ApplicationDependencyService applicationDependencyService;
  @Mock private ApplicationDependencyRepository applicationDependencyRepository;
  @Mock private ApplicationDependencyMapper applicationDependencyMapper;

  @Test
  void saveEntitlementDependencies_positive() {
    var dependency = applicationDependency("test-app", "0.0.1");
    var dependencies = List.of(dependency);
    var expectedDependencyEntity = applicationDependencyEntity(dependency);

    when(applicationDependencyMapper.map(TENANT_ID, APPLICATION_ID, dependency)).thenReturn(expectedDependencyEntity);

    applicationDependencyService.saveEntitlementDependencies(TENANT_ID, APPLICATION_ID, dependencies);

    var expectedEntity = applicationDependencyEntity(dependency);
    verify(applicationDependencyRepository).saveAll(Set.of(expectedEntity));
  }

  @Test
  void saveEntitlementDependencies_positive_emptyDependencyIds() {
    applicationDependencyService.saveEntitlementDependencies(TENANT_ID, APPLICATION_ID, emptyList());
    verifyNoInteractions(applicationDependencyRepository);
  }
}
