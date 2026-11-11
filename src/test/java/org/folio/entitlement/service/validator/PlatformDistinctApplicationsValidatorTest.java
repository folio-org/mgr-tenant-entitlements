package org.folio.entitlement.service.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.PLATFORM_ENTITLE;
import static org.folio.entitlement.service.flow.PlatformTestValues.platformContext;
import static org.folio.entitlement.service.flow.PlatformTestValues.platformRequest;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;

import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class PlatformDistinctApplicationsValidatorTest {

  private final PlatformDistinctApplicationsValidator validator = new PlatformDistinctApplicationsValidator();

  @Test
  void execute_positive_everyApplicationNameIsDistinct() {
    var context = platformContext(
      platformRequest(PLATFORM_ENTITLE, OKAPI_TOKEN, "platform-app-1.0.0", "other-platform-1.0.0"));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_negative_sameApplicationTwiceIsRejected() {
    var context = platformContext(
      platformRequest(PLATFORM_ENTITLE, OKAPI_TOKEN, "platform-app-1.0.0", "platform-app-2.0.0"));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Request contains the same application more than once")
      .satisfies(exception -> assertThat(((RequestValidationException) exception).getErrorParameters())
        .extracting(Parameter::getKey)
        .containsExactlyInAnyOrder("platform-app-1.0.0", "platform-app-2.0.0"));
  }

  @Test
  void execute_negative_onlyDuplicatedApplicationsAreReported() {
    var context = platformContext(platformRequest(PLATFORM_ENTITLE, OKAPI_TOKEN,
      "platform-app-1.0.0", "other-platform-1.0.0", "platform-app-2.0.0"));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .satisfies(exception -> assertThat(((RequestValidationException) exception).getErrorParameters())
        .extracting(Parameter::getKey)
        .containsExactlyInAnyOrder("platform-app-1.0.0", "platform-app-2.0.0"));
  }
}
