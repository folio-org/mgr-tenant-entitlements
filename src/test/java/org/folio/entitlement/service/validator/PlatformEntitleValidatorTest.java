package org.folio.entitlement.service.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PlatformEntitleValidatorTest {

  private static final String APP_V1 = "app-platform-1.0.0";
  private static final String APP_V2 = "app-platform-2.0.0";

  @InjectMocks private PlatformEntitleValidator validator;
  @Mock private PlatformEntitlementRepository repository;

  @Test
  void execute_positive_nothingIsEntitled() {
    when(repository.findById("app-platform")).thenReturn(Optional.empty());
    var context = platformContext(platformRequest(APP_V1, ENTITLE, OKAPI_TOKEN));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_sameVersionIsEntitledRetry() {
    when(repository.findById("app-platform")).thenReturn(Optional.of(PlatformEntitlementEntity.of(APP_V1)));
    var context = platformContext(platformRequest(APP_V1, ENTITLE, OKAPI_TOKEN));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_negative_anotherVersionIsEntitled() {
    when(repository.findById("app-platform")).thenReturn(Optional.of(PlatformEntitlementEntity.of(APP_V1)));
    var context = platformContext(platformRequest(APP_V2, ENTITLE, OKAPI_TOKEN));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Another version of the application is already entitled");
  }
}
