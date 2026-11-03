package org.folio.entitlement.service.validator;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_APP_DESCRIPTORS;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestValues.appDescriptor;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.folio.entitlement.support.TestValues.dependency;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.common.domain.model.ApplicationScope;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class PlatformStateRequestValidatorTest {

  private final PlatformStateRequestValidator validator = new PlatformStateRequestValidator();

  @Test
  void execute_positive_onlyRegularApplicationsRequested() {
    var regular = appDescriptor("folio-app1-1.0.0").scope(ApplicationScope.REGULAR);

    assertThatCode(() -> validator.execute(context(regular))).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_platformApplicationReachedOnlyAsDependencyIsNotInRequestedDescriptors() {
    // a platform application that is only a dependency is not among the directly requested descriptors
    var requested = appDescriptor("folio-app3-3.0.0", dependency("platform-app-1.0.0"))
      .scope(ApplicationScope.REGULAR);

    assertThatCode(() -> validator.execute(context(requested))).doesNotThrowAnyException();
  }

  @Test
  void execute_negative_directlyRequestedPlatformApplicationIsRejected() {
    var regular = appDescriptor("folio-app1-1.0.0").scope(ApplicationScope.REGULAR);
    var platform = appDescriptor("platform-app-1.0.0").scope(ApplicationScope.PLATFORM);
    var context = context(regular, platform);

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Desired state request contains platform-scoped applications")
      .satisfies(exception -> assertThat(((RequestValidationException) exception).getErrorParameters())
        .singleElement()
        .satisfies(parameter -> {
          assertThat(parameter.getKey()).isEqualTo("platform-app-1.0.0");
          assertThat(parameter.getValue())
            .isEqualTo("Platform-scoped application cannot be set in a tenant desired state");
        }));
  }

  private static CommonStageContext context(ApplicationDescriptor... descriptors) {
    var contextData = new HashMap<>(Map.of(PARAM_APP_DESCRIPTORS, List.of(descriptors)));
    return commonStageContext(FLOW_ID, emptyMap(), contextData);
  }
}
