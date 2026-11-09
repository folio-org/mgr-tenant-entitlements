package org.folio.entitlement.service.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.PLATFORM_UPGRADE;
import static org.folio.entitlement.service.flow.PlatformTestValues.platformContext;
import static org.folio.entitlement.service.flow.PlatformTestValues.platformRequest;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.folio.entitlement.domain.entity.ApplicationDependencyEntity;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.repository.ApplicationDependencyRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PlatformTenantDependentsValidatorTest {

  private static final UUID OTHER_TENANT_ID = UUID.fromString("0fa1b2c3-1111-4222-8333-444455556666");

  @InjectMocks private PlatformTenantDependentsValidator validator;
  @Mock private ApplicationDependencyRepository dependencyRepository;

  @Test
  void execute_positive_upgradeSatisfiesEveryDependent() {
    when(dependencyRepository.findByParentNameIn(List.of("platform-app"))).thenReturn(List.of(
      dependent(TENANT_ID, "folio-app3-3.0.0", "^1.0.0"),
      dependent(OTHER_TENANT_ID, "folio-app4-4.0.0", ">=1.0.0")));
    var context = platformContext(platformRequest(PLATFORM_UPGRADE, OKAPI_TOKEN, "platform-app-1.5.0"));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_upgradeWithoutDependents() {
    when(dependencyRepository.findByParentNameIn(List.of("platform-app"))).thenReturn(List.of());
    var context = platformContext(platformRequest(PLATFORM_UPGRADE, OKAPI_TOKEN, "platform-app-2.0.0"));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_negative_oneTenantDependentIsNotSatisfiedRejectsWholeUpgrade() {
    when(dependencyRepository.findByParentNameIn(List.of("platform-app"))).thenReturn(List.of(
      dependent(TENANT_ID, "folio-app3-3.0.0", "^1.0.0"),
      dependent(OTHER_TENANT_ID, "folio-app4-4.0.0", "^2.0.0")));
    var context = platformContext(platformRequest(PLATFORM_UPGRADE, OKAPI_TOKEN, "platform-app-2.0.0"));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Platform application upgrade is incompatible with dependent applications")
      .satisfies(exception -> assertThat(((RequestValidationException) exception).getErrorParameters())
        .singleElement()
        .satisfies(parameter -> {
          assertThat(parameter.getKey()).isEqualTo(TENANT_ID + ":folio-app3-3.0.0");
          assertThat(parameter.getValue()).contains("^1.0.0").contains("2.0.0");
        }));
  }

  @Test
  void execute_negative_dependentOfOneOfSeveralUpgradedApplicationsRejectsWholeUpgrade() {
    when(dependencyRepository.findByParentNameIn(anyList())).thenReturn(List.of(
      dependent(TENANT_ID, "folio-app3-3.0.0", "^1.0.0"),
      dependent(OTHER_TENANT_ID, "folio-app4-4.0.0", "^1.0.0", "other-platform")));
    var context = platformContext(
      platformRequest(PLATFORM_UPGRADE, OKAPI_TOKEN, "platform-app-1.5.0", "other-platform-2.0.0"));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .satisfies(exception -> assertThat(((RequestValidationException) exception).getErrorParameters())
        .singleElement()
        .satisfies(parameter -> assertThat(parameter.getKey()).isEqualTo(OTHER_TENANT_ID + ":folio-app4-4.0.0")));
  }

  private static ApplicationDependencyEntity dependent(UUID tenantId, String applicationId, String parentRange) {
    return dependent(tenantId, applicationId, parentRange, "platform-app");
  }

  private static ApplicationDependencyEntity dependent(UUID tenantId, String applicationId, String parentRange,
    String parentName) {
    var entity = new ApplicationDependencyEntity();
    entity.setTenantId(tenantId);
    entity.setApplicationId(applicationId);
    entity.setParentName(parentName);
    entity.setParentVersion(parentRange);
    entity.setOptional(false);
    return entity;
  }
}
