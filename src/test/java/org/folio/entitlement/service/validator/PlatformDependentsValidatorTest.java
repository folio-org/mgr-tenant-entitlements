package org.folio.entitlement.service.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.PLATFORM_UPGRADE;
import static org.folio.entitlement.service.flow.PlatformTestValues.platformContext;
import static org.folio.entitlement.service.flow.PlatformTestValues.platformRequest;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.entitlement.domain.entity.PlatformApplicationDependencyEntity;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.repository.PlatformApplicationDependencyRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PlatformDependentsValidatorTest {

  @InjectMocks private PlatformDependentsValidator validator;
  @Mock private PlatformApplicationDependencyRepository dependencyRepository;

  @Test
  void execute_positive_upgradeSatisfiesEveryDependent() {
    when(dependencyRepository.findByParentNameIn(List.of("platform-base"))).thenReturn(List.of(
      dependent("platform-app-1.0.0", "platform-base", "^1.0.0"),
      dependent("platform-app2-1.0.0", "platform-base", ">=1.0.0")));
    var context = platformContext(platformRequest(PLATFORM_UPGRADE, OKAPI_TOKEN, "platform-base-1.5.0"));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_upgradeWithoutDependents() {
    when(dependencyRepository.findByParentNameIn(List.of("platform-base"))).thenReturn(List.of());
    var context = platformContext(platformRequest(PLATFORM_UPGRADE, OKAPI_TOKEN, "platform-base-2.0.0"));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_dependentUpgradedByTheSameRequestIsSkipped() {
    when(dependencyRepository.findByParentNameIn(anyList())).thenReturn(List.of(
      dependent("platform-app-1.0.0", "platform-base", "^1.0.0")));
    var context = platformContext(
      platformRequest(PLATFORM_UPGRADE, OKAPI_TOKEN, "platform-base-2.0.0", "platform-app-2.0.0"));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_negative_oneDependentIsNotSatisfiedRejectsWholeUpgrade() {
    when(dependencyRepository.findByParentNameIn(List.of("platform-base"))).thenReturn(List.of(
      dependent("platform-app-1.0.0", "platform-base", "^1.0.0"),
      dependent("platform-app2-1.0.0", "platform-base", "^2.0.0")));
    var context = platformContext(platformRequest(PLATFORM_UPGRADE, OKAPI_TOKEN, "platform-base-2.0.0"));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Platform application upgrade is incompatible with dependent applications")
      .satisfies(exception -> assertThat(((RequestValidationException) exception).getErrorParameters())
        .singleElement()
        .satisfies(parameter -> {
          assertThat(parameter.getKey()).isEqualTo("platform-app-1.0.0");
          assertThat(parameter.getValue())
            .isEqualTo("Requires platform-base ^1.0.0, which the upgrade to 2.0.0 does not satisfy");
        }));
  }

  @Test
  void execute_positive_optionalDependentIsNotChecked() {
    var optional = dependent("platform-app-1.0.0", "platform-base", "^1.0.0");
    optional.setOptional(true);
    when(dependencyRepository.findByParentNameIn(List.of("platform-base"))).thenReturn(List.of(optional));
    var context = platformContext(platformRequest(PLATFORM_UPGRADE, OKAPI_TOKEN, "platform-base-2.0.0"));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  private static PlatformApplicationDependencyEntity dependent(String applicationId, String parentName,
    String parentRange) {
    var entity = new PlatformApplicationDependencyEntity();
    entity.setApplicationId(applicationId);
    entity.setApplicationName(applicationId.substring(0, applicationId.lastIndexOf('-')));
    entity.setParentName(parentName);
    entity.setParentVersion(parentRange);
    entity.setOptional(false);
    return entity;
  }
}
