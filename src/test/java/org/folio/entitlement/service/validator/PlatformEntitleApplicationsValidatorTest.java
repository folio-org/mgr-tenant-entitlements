package org.folio.entitlement.service.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.PLATFORM_ENTITLE;
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
class PlatformEntitleApplicationsValidatorTest {

  private static final String APPLICATION_ID = "platform-app-1.0.0";
  private static final String OTHER_APPLICATION_ID = "other-platform-1.0.0";
  private static final String REGULAR_APPLICATION_ID = "regular-app-1.0.0";

  @InjectMocks private PlatformEntitleApplicationsValidator validator;
  @Mock private ApplicationManagerService applicationManagerService;

  @Test
  void execute_positive_platformScopedApplicationIsAccepted() {
    stubScope(APPLICATION_ID, ApplicationScope.PLATFORM);
    var context = platformContext(platformRequest(PLATFORM_ENTITLE, OKAPI_TOKEN, APPLICATION_ID));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_everyApplicationIsPlatformScoped() {
    stubScope(APPLICATION_ID, ApplicationScope.PLATFORM);
    stubScope(OTHER_APPLICATION_ID, ApplicationScope.PLATFORM);
    var context = platformContext(platformRequest(PLATFORM_ENTITLE, OKAPI_TOKEN, APPLICATION_ID, OTHER_APPLICATION_ID));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_negative_regularApplicationIsRejected() {
    stubScope(REGULAR_APPLICATION_ID, ApplicationScope.REGULAR);
    var context = platformContext(platformRequest(PLATFORM_ENTITLE, OKAPI_TOKEN, REGULAR_APPLICATION_ID));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Application is not platform-scoped")
      .satisfies(exception -> assertThat(((RequestValidationException) exception).getErrorParameters())
        .singleElement()
        .satisfies(parameter -> {
          assertThat(parameter.getKey()).isEqualTo(REGULAR_APPLICATION_ID);
          assertThat(parameter.getValue()).isEqualTo("Application is not platform-scoped");
        }));
  }

  @Test
  void execute_negative_oneRegularApplicationRejectsWholeRequest() {
    stubScope(APPLICATION_ID, ApplicationScope.PLATFORM);
    stubScope(REGULAR_APPLICATION_ID, ApplicationScope.REGULAR);
    var context = platformContext(platformRequest(PLATFORM_ENTITLE, OKAPI_TOKEN, APPLICATION_ID, REGULAR_APPLICATION_ID));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Application is not platform-scoped")
      .satisfies(exception -> assertThat(((RequestValidationException) exception).getErrorParameters())
        .singleElement()
        .satisfies(parameter -> assertThat(parameter.getKey()).isEqualTo(REGULAR_APPLICATION_ID)));
  }

  @Test
  void execute_negative_sameApplicationTwiceIsRejected() {
    var context = platformContext(platformRequest(PLATFORM_ENTITLE, OKAPI_TOKEN, "platform-app-1.0.0", "platform-app-2.0.0"));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Request contains the same application more than once")
      .satisfies(exception -> assertThat(((RequestValidationException) exception).getErrorParameters())
        .extracting(parameter -> parameter.getKey())
        .containsExactlyInAnyOrder("platform-app-1.0.0", "platform-app-2.0.0"));
  }

  private void stubScope(String applicationId, ApplicationScope scope) {
    var descriptor = appDescriptor(applicationId).scope(scope);
    when(applicationManagerService.getApplicationDescriptor(applicationId, OKAPI_TOKEN)).thenReturn(descriptor);
  }
}
