package org.folio.entitlement.service.stage;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestValues.appDescriptor;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.dependency;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.Dependency;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PlatformEntitleDependencyValidatorTest {

  private static final String ERROR_MESSAGE = "The following platform application dependencies must be entitled first";

  @InjectMocks private PlatformEntitleDependencyValidator validator;
  @Mock private PlatformEntitlementRepository repository;

  @Test
  void execute_positive_noDependencies() {
    assertThatCode(() -> validator.execute(stageContext())).doesNotThrowAnyException();

    verifyNoInteractions(repository);
  }

  @Test
  void execute_positive_dependencyIsEntitledWithSatisfyingVersion() {
    when(repository.findByApplicationNameIn(List.of("dep"))).thenReturn(List.of(PlatformEntitlementEntity.of("dep-1.2.0")));

    var context = stageContext(Dependency.of("dep", "^1.0.0", false));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_negative_onlyMissingDependenciesAreReported() {
    when(repository.findByApplicationNameIn(List.of("dep1", "dep2")))
      .thenReturn(List.of(PlatformEntitlementEntity.of("dep1-1.0.0")));
    var missing = dependency("dep2-1.0.0");
    var context = stageContext(dependency("dep1-1.0.0"), missing);

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage(ERROR_MESSAGE)
      .satisfies(e -> assertDependenciesParameter(e, missing.nameVersion()));
  }

  @Test
  void execute_positive_optionalDependencyIsNotChecked() {
    var optional = dependency("dep-1.0.0");
    optional.setOptional(true);

    assertThatCode(() -> validator.execute(stageContext(optional))).doesNotThrowAnyException();

    verifyNoInteractions(repository);
  }

  @Test
  void execute_negative_dependencyIsNotEntitled() {
    when(repository.findByApplicationNameIn(List.of("dep"))).thenReturn(List.of());
    var missing = dependency("dep-1.0.0");
    var context = stageContext(missing);

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage(ERROR_MESSAGE)
      .satisfies(e -> assertDependenciesParameter(e, missing.nameVersion()));
  }

  @Test
  void execute_negative_entitledVersionDoesNotSatisfyDependency() {
    when(repository.findByApplicationNameIn(List.of("dep"))).thenReturn(List.of(PlatformEntitlementEntity.of("dep-2.0.0")));
    var required = Dependency.of("dep", "^1.0.0", false);
    var context = stageContext(required);

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage(ERROR_MESSAGE)
      .satisfies(e -> assertDependenciesParameter(e, required.nameVersion()));
  }

  private static void assertDependenciesParameter(Throwable throwable, String expectedValue) {
    assertThat(((RequestValidationException) throwable).getErrorParameters())
      .singleElement()
      .satisfies(parameter -> {
        assertThat(parameter.getKey()).isEqualTo("dependencies");
        assertThat(parameter.getValue()).isEqualTo(expectedValue);
      });
  }

  private static ApplicationStageContext stageContext(Dependency... dependencies) {
    var flowParameters = Map.of(
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_APPLICATION_DESCRIPTOR, appDescriptor(APPLICATION_ID, dependencies));

    return appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());
  }
}
