package org.folio.entitlement.service.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.UPGRADE;
import static org.folio.entitlement.service.flow.PlatformTestValues.platformContext;
import static org.folio.entitlement.service.flow.PlatformTestValues.platformRequest;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PlatformEntitlementExistToUpgradeValidatorTest {

  private static final String APP_V1 = "app-platform-1.0.0";
  private static final String APP_V2 = "app-platform-2.0.0";
  private static final String OTHER_V1 = "other-platform-1.0.0";
  private static final String OTHER_V2 = "other-platform-2.0.0";

  @InjectMocks private PlatformEntitlementExistToUpgradeValidator validator;
  @Mock private PlatformEntitlementRepository repository;

  @Test
  void execute_positive_higherVersion() {
    when(repository.findAllById(List.of("app-platform"))).thenReturn(List.of(PlatformEntitlementEntity.of(APP_V1)));
    var context = platformContext(platformRequest(UPGRADE, OKAPI_TOKEN, APP_V2));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_sameVersionIsEntitledRetry() {
    when(repository.findAllById(List.of("app-platform"))).thenReturn(List.of(PlatformEntitlementEntity.of(APP_V2)));
    var context = platformContext(platformRequest(UPGRADE, OKAPI_TOKEN, APP_V2));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_everyApplicationHasHigherVersion() {
    when(repository.findAllById(List.of("app-platform", "other-platform")))
      .thenReturn(List.of(PlatformEntitlementEntity.of(APP_V1), PlatformEntitlementEntity.of(OTHER_V1)));
    var context = platformContext(platformRequest(UPGRADE, OKAPI_TOKEN, APP_V2, OTHER_V2));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_negative_nothingIsEntitled() {
    when(repository.findAllById(List.of("app-platform"))).thenReturn(List.of());
    var context = platformContext(platformRequest(UPGRADE, OKAPI_TOKEN, APP_V2));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid applications provided for platform upgrade")
      .satisfies(exception -> assertThat(((RequestValidationException) exception).getErrorParameters())
        .singleElement()
        .satisfies(parameter -> {
          assertThat(parameter.getKey()).isEqualTo(APP_V2);
          assertThat(parameter.getValue()).isEqualTo("No version of the application is entitled to upgrade");
        }));
  }

  @ParameterizedTest
  @ValueSource(strings = {"app-platform-1.0.0", "app-platform-1.9.9"})
  void execute_negative_lowerVersion(String lowerVersion) {
    var entitled = PlatformEntitlementEntity.of("app-platform-2.0.0");
    when(repository.findAllById(List.of("app-platform"))).thenReturn(List.of(entitled));
    var context = platformContext(platformRequest(UPGRADE, OKAPI_TOKEN, lowerVersion));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid applications provided for platform upgrade")
      .satisfies(exception -> assertThat(((RequestValidationException) exception).getErrorParameters())
        .singleElement()
        .satisfies(parameter -> assertThat(parameter.getValue()).contains("not higher than entitled")));
  }

  @Test
  void execute_negative_oneInvalidApplicationRejectsWholeRequest() {
    when(repository.findAllById(List.of("app-platform", "other-platform")))
      .thenReturn(List.of(PlatformEntitlementEntity.of(APP_V1)));
    var context = platformContext(platformRequest(UPGRADE, OKAPI_TOKEN, APP_V2, OTHER_V2));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .satisfies(exception -> assertThat(((RequestValidationException) exception).getErrorParameters())
        .singleElement()
        .satisfies(parameter -> assertThat(parameter.getKey()).isEqualTo(OTHER_V2)));
  }
}
