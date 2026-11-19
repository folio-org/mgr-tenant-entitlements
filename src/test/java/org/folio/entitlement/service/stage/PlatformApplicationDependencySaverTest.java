package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_PLATFORM_DEPENDENCIES_PREEXISTING;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestValues.appDescriptor;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.dependency;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.Dependency;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.folio.entitlement.service.PlatformApplicationDependencyService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PlatformApplicationDependencySaverTest {

  private static final Dependency DEPENDENCY = dependency("dep-1.0.0");
  private static final String APPLICATION_NAME = SemverUtils.getName(APPLICATION_ID);

  @InjectMocks private PlatformApplicationDependencySaver dependencySaver;
  @Mock private PlatformApplicationDependencyService dependencyService;
  @Mock private PlatformEntitlementRepository entitlementRepository;

  @Test
  void execute_positive_newApplicationDependenciesAreSaved() {
    when(entitlementRepository.existsById(APPLICATION_NAME)).thenReturn(false);
    var context = stageContext(new HashMap<>());

    dependencySaver.execute(context);

    verify(dependencyService).saveDependencies(APPLICATION_ID, List.of(DEPENDENCY));
    assertThat(context.isPlatformDependenciesPreexisting()).isFalse();
  }

  @Test
  void execute_positive_alreadyEntitledApplicationDependenciesAreNotSavedAgain() {
    when(entitlementRepository.existsById(APPLICATION_NAME)).thenReturn(true);
    var context = stageContext(new HashMap<>());

    dependencySaver.execute(context);

    verifyNoInteractions(dependencyService);
    assertThat(context.isPlatformDependenciesPreexisting()).isTrue();
  }

  @Test
  void cancel_positive_newApplicationDependenciesAreDeleted() {
    dependencySaver.cancel(stageContext(new HashMap<>(Map.of(PARAM_PLATFORM_DEPENDENCIES_PREEXISTING, false))));

    verify(dependencyService).deleteDependencies(APPLICATION_ID, List.of(DEPENDENCY));
  }

  @Test
  void cancel_positive_dependenciesOfEntitledApplicationAreKept() {
    dependencySaver.cancel(stageContext(new HashMap<>(Map.of(PARAM_PLATFORM_DEPENDENCIES_PREEXISTING, true))));

    verifyNoInteractions(dependencyService);
  }

  private static ApplicationStageContext stageContext(Map<?, ?> contextData) {
    var flowParameters = Map.of(
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_APPLICATION_DESCRIPTOR, appDescriptor(APPLICATION_ID, DEPENDENCY));

    return appStageContext(FLOW_STAGE_ID, flowParameters, contextData);
  }
}
