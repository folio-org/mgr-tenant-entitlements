package org.folio.entitlement.service.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PlatformEntitlementExistValidatorTest {

  private static final String APP_V1 = "app-platform-1.0.0";
  private static final String APP_V2 = "app-platform-2.0.0";
  private static final String OTHER_V1 = "other-platform-1.0.0";

  @InjectMocks private PlatformEntitlementExistValidator validator;
  @Mock private PlatformEntitlementRepository repository;

  @Test
  void execute_positive_nothingIsEntitled() {
    when(repository.findAllById(List.of("app-platform"))).thenReturn(List.of());
    var context = platformContext(platformRequest(ENTITLE, OKAPI_TOKEN, APP_V1));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_sameVersionIsEntitledRetry() {
    when(repository.findAllById(List.of("app-platform"))).thenReturn(List.of(PlatformEntitlementEntity.of(APP_V1)));
    var context = platformContext(platformRequest(ENTITLE, OKAPI_TOKEN, APP_V1));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_negative_anotherVersionIsEntitled() {
    when(repository.findAllById(List.of("app-platform"))).thenReturn(List.of(PlatformEntitlementEntity.of(APP_V1)));
    var context = platformContext(platformRequest(ENTITLE, OKAPI_TOKEN, APP_V2));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Another version of the application is already entitled");
  }

  @Test
  void execute_negative_oneApplicationConflictsRejectsWholeRequest() {
    when(repository.findAllById(List.of("other-platform", "app-platform")))
      .thenReturn(List.of(PlatformEntitlementEntity.of(APP_V1)));
    var context = platformContext(platformRequest(ENTITLE, OKAPI_TOKEN, OTHER_V1, APP_V2));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Another version of the application is already entitled")
      .satisfies(exception -> assertThat(((RequestValidationException) exception).getErrorParameters())
        .singleElement()
        .satisfies(parameter -> assertThat(parameter.getKey()).isEqualTo(APP_V2)));
  }
}
