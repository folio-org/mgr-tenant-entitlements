package org.folio.entitlement.service.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.UPGRADE;
import static org.folio.entitlement.service.flow.PlatformTestValues.platformContext;
import static org.folio.entitlement.service.flow.PlatformTestValues.platformRequest;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.mockito.Mockito.when;

import java.util.Optional;
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
class PlatformUpgradeValidatorTest {

  private static final String APP_V1 = "app-platform-1.0.0";
  private static final String APP_V2 = "app-platform-2.0.0";

  @InjectMocks private PlatformUpgradeValidator validator;
  @Mock private PlatformEntitlementRepository repository;

  @Test
  void execute_positive_higherVersion() {
    when(repository.findById("app-platform")).thenReturn(Optional.of(PlatformEntitlementEntity.of(APP_V1)));
    var context = platformContext(platformRequest(APP_V2, UPGRADE, OKAPI_TOKEN));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_sameVersionIsEntitledRetry() {
    when(repository.findById("app-platform")).thenReturn(Optional.of(PlatformEntitlementEntity.of(APP_V2)));
    var context = platformContext(platformRequest(APP_V2, UPGRADE, OKAPI_TOKEN));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_negative_nothingIsEntitled() {
    when(repository.findById("app-platform")).thenReturn(Optional.empty());
    var context = platformContext(platformRequest(APP_V2, UPGRADE, OKAPI_TOKEN));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("No version of the application is entitled to upgrade");
  }

  @ParameterizedTest
  @ValueSource(strings = {"app-platform-1.0.0", "app-platform-1.9.9"})
  void execute_negative_lowerVersion(String lowerVersion) {
    var entitled = PlatformEntitlementEntity.of("app-platform-2.0.0");
    when(repository.findById("app-platform")).thenReturn(Optional.of(entitled));
    var context = platformContext(platformRequest(lowerVersion, UPGRADE, OKAPI_TOKEN));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Application version is invalid or not higher than entitled");
  }
}
