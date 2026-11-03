package org.folio.entitlement.service.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.service.flow.PlatformTestValues.platformContext;
import static org.folio.entitlement.service.flow.PlatformTestValues.platformRequest;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestValues.appDescriptor;
import static org.mockito.Mockito.when;

import org.folio.common.domain.model.ApplicationScope;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PlatformScopeValidatorTest {

  private static final String APPLICATION_ID = "platform-app-1.0.0";

  @InjectMocks private PlatformScopeValidator validator;
  @Mock private ApplicationManagerService applicationManagerService;

  @Test
  void execute_positive_platformScopedApplicationIsAccepted() {
    var descriptor = appDescriptor(APPLICATION_ID).scope(ApplicationScope.PLATFORM);
    when(applicationManagerService.getApplicationDescriptor(APPLICATION_ID, OKAPI_TOKEN)).thenReturn(descriptor);
    var context = platformContext(platformRequest(APPLICATION_ID, ENTITLE, OKAPI_TOKEN));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_negative_regularApplicationIsRejected() {
    var descriptor = appDescriptor(APPLICATION_ID).scope(ApplicationScope.REGULAR);
    when(applicationManagerService.getApplicationDescriptor(APPLICATION_ID, OKAPI_TOKEN)).thenReturn(descriptor);
    var context = platformContext(platformRequest(APPLICATION_ID, ENTITLE, OKAPI_TOKEN));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Application is not platform-scoped")
      .satisfies(exception -> assertThat(((RequestValidationException) exception).getErrorParameters())
        .singleElement()
        .satisfies(parameter -> {
          assertThat(parameter.getKey()).isEqualTo("applicationId");
          assertThat(parameter.getValue()).isEqualTo(APPLICATION_ID);
        }));
  }
}
