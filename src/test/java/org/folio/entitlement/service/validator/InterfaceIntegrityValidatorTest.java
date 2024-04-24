package org.folio.entitlement.service.validator;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_APP_DESCRIPTORS;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.ApplicationDependencyValidatorService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class InterfaceIntegrityValidatorTest {

  @InjectMocks private InterfaceIntegrityValidator interfaceIntegrityValidator;
  @Mock private ApplicationDependencyValidatorService validatorService;

  @Test
  void execute_positive() {
    var applicationDescriptors = List.of(applicationDescriptor());
    var stageParameters = Map.of(PARAM_APP_DESCRIPTORS, applicationDescriptors);
    var stageContext = commonStageContext(FLOW_ID, emptyMap(), stageParameters);

    interfaceIntegrityValidator.execute(stageContext);

    verify(validatorService).validateDescriptors(applicationDescriptors);
  }

  @Test
  void execute_negative() {
    var applicationDescriptors = List.of(applicationDescriptor());
    var exception = new RequestValidationException("Invalid interface dependency", "application", APPLICATION_ID);

    doThrow(exception).when(validatorService).validateDescriptors(applicationDescriptors);

    var stageParameters = Map.of(PARAM_APP_DESCRIPTORS, applicationDescriptors);
    var stageContext = commonStageContext(FLOW_ID, emptyMap(), stageParameters);
    assertThatThrownBy(() -> interfaceIntegrityValidator.execute(stageContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid interface dependency")
      .satisfies(error ->
        assertThat(((RequestValidationException) error).getErrorParameters())
          .containsExactly(new Parameter().key("application").value(APPLICATION_ID)));
  }

  @Test
  void validate_positive() {
    var request = entitlementRequest();
    interfaceIntegrityValidator.validate(request);
    verify(validatorService).validateApplications(request);
  }

  @Test
  void validate_negative() {
    var request = entitlementRequest();
    var exception = new RequestValidationException("Invalid interface dependency", "application", APPLICATION_ID);
    doThrow(exception).when(validatorService).validateApplications(request);

    assertThatThrownBy(() -> interfaceIntegrityValidator.validate(request))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid interface dependency")
      .satisfies(error ->
        assertThat(((RequestValidationException) error).getErrorParameters())
          .containsExactly(new Parameter().key("application").value(APPLICATION_ID)));
  }

  @ParameterizedTest
  @DisplayName("shouldValidate_parameterized")
  @CsvSource({"ENTITLE,true", "REVOKE,false", "UPGRADE,false", ",false"})
  void shouldValidate_parameterized(EntitlementType type, boolean expected) {
    var request = EntitlementRequest.builder().type(type).build();
    var result = interfaceIntegrityValidator.shouldValidate(request);
    assertThat(result).isEqualTo(expected);
  }

  private static EntitlementRequest entitlementRequest() {
    return EntitlementRequest.builder()
      .applications(List.of(APPLICATION_ID))
      .tenantId(TENANT_ID)
      .okapiToken(OKAPI_TOKEN)
      .type(ENTITLE)
      .build();
  }
}
