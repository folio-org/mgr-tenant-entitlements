package org.folio.entitlement.service.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Set;
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

  @InjectMocks private InterfaceIntegrityValidator validator;
  @Mock private ApplicationDependencyValidatorService validatorService;

  @Test
  void validate_positive() {
    var request = entitlementRequest();
    var applicationIds = Set.of(APPLICATION_ID);
    doNothing().when(validatorService).validateApplications(any(), anySet(), anyString());

    validator.validate(request);

    verify(validatorService).validateApplications(TENANT_ID, applicationIds, OKAPI_TOKEN);
  }

  @Test
  void validate_negative() {
    var request = entitlementRequest();
    var applicationIds = Set.of(APPLICATION_ID);
    var exception = new RequestValidationException("Invalid interface dependency", "application", APPLICATION_ID);
    doThrow(exception).when(validatorService).validateApplications(TENANT_ID, applicationIds, OKAPI_TOKEN);

    assertThatThrownBy(() -> validator.validate(request))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid interface dependency")
      .satisfies(error ->
        assertThat(((RequestValidationException) error).getErrorParameters())
          .containsExactly(new Parameter().key("application").value(APPLICATION_ID)));
  }

  @ParameterizedTest
  @DisplayName("shouldValidate_parameterized")
  @CsvSource({"ENTITLE, true", "REVOKE, false"})
  void shouldValidate_parameterized(EntitlementType type, boolean expected) {
    var request = EntitlementRequest.builder().type(type).build();
    var result = validator.shouldValidate(request);
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
