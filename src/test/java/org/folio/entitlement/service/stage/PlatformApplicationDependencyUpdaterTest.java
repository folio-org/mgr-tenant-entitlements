package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_ENTITLED_APPLICATION_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_ENTITLED_APPLICATION_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_PLATFORM_DEPENDENCIES_PREEXISTING;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestValues.appDescriptor;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.dependency;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.Dependency;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.service.PlatformApplicationDependencyService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PlatformApplicationDependencyUpdaterTest {

  private static final String ENTITLED_ID = "platform-app-1.0.0";
  private static final String UPGRADED_ID = "platform-app-2.0.0";
  private static final Dependency OLD_DEPENDENCY = dependency("dep-1.0.0");
  private static final Dependency NEW_DEPENDENCY = dependency("dep-2.0.0");

  @InjectMocks private PlatformApplicationDependencyUpdater dependencyUpdater;
  @Mock private PlatformApplicationDependencyService dependencyService;

  @Test
  void execute_positive_dependenciesOfEntitledVersionAreReplaced() {
    var context = stageContext(UPGRADED_ID, new HashMap<>());

    dependencyUpdater.execute(context);

    var inOrder = inOrder(dependencyService);
    inOrder.verify(dependencyService).deleteDependencies(ENTITLED_ID, List.of(OLD_DEPENDENCY));
    inOrder.verify(dependencyService).saveDependencies(UPGRADED_ID, List.of(NEW_DEPENDENCY));
    assertThat(context.isPlatformDependenciesPreexisting()).isFalse();
  }

  @Test
  void execute_positive_sameVersionDependenciesAreKept() {
    var context = stageContext(ENTITLED_ID, new HashMap<>());

    dependencyUpdater.execute(context);

    verifyNoInteractions(dependencyService);
    assertThat(context.isPlatformDependenciesPreexisting()).isTrue();
  }

  @Test
  void cancel_positive_dependenciesOfEntitledVersionAreRestored() {
    var context = stageContext(UPGRADED_ID, new HashMap<>(Map.of(PARAM_PLATFORM_DEPENDENCIES_PREEXISTING, false)));

    dependencyUpdater.cancel(context);

    var inOrder = inOrder(dependencyService);
    inOrder.verify(dependencyService).deleteDependencies(UPGRADED_ID, List.of(NEW_DEPENDENCY));
    inOrder.verify(dependencyService).saveDependencies(ENTITLED_ID, List.of(OLD_DEPENDENCY));
  }

  @Test
  void cancel_positive_sameVersionDependenciesAreKept() {
    var context = stageContext(ENTITLED_ID, new HashMap<>(Map.of(PARAM_PLATFORM_DEPENDENCIES_PREEXISTING, true)));

    dependencyUpdater.cancel(context);

    verifyNoInteractions(dependencyService);
  }

  private static ApplicationStageContext stageContext(String applicationId, Map<?, ?> contextData) {
    var flowParameters = Map.of(
      PARAM_APPLICATION_ID, applicationId,
      PARAM_APPLICATION_DESCRIPTOR, appDescriptor(applicationId, NEW_DEPENDENCY),
      PARAM_ENTITLED_APPLICATION_ID, ENTITLED_ID,
      PARAM_ENTITLED_APPLICATION_DESCRIPTOR, appDescriptor(ENTITLED_ID, OLD_DEPENDENCY));

    return appStageContext(FLOW_STAGE_ID, flowParameters, contextData);
  }
}
