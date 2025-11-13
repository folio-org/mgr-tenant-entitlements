package org.folio.entitlement.service.validator;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.REVOKE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.EntitlementCrudService;
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
class ExistingEntitlementValidatorTest {

  @InjectMocks private ExistingEntitlementValidator validator;
  @Mock private EntitlementCrudService entitlementCrudService;

  @Test
  void execute_positive() {
    var entitlementRequest = entitlementRequest();
    when(entitlementCrudService.getEntitlements(any())).thenReturn(List.of(entitlement()));

    var stageContext = commonStageContext(FLOW_ID, Map.of(PARAM_REQUEST, entitlementRequest), emptyMap());
    validator.execute(stageContext);

    verify(entitlementCrudService).getEntitlements(entitlementRequest);
  }

  @Test
  void execute_negative() {
    var entitlementRequest = entitlementRequest();
    when(entitlementCrudService.getEntitlements(entitlementRequest)).thenReturn(emptyList());

    var stageContext = commonStageContext(FLOW_ID, Map.of(PARAM_REQUEST, entitlementRequest), emptyMap());
    assertThatThrownBy(() -> validator.execute(stageContext))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage("Entitlements are not found for applications: [test-app-1.0.0]");
  }

  @Test
  void validate_positive() {
    var entitlementRequest = entitlementRequest();
    when(entitlementCrudService.getEntitlements(any())).thenReturn(List.of(entitlement()));
    validator.validate(entitlementRequest);
    verify(entitlementCrudService).getEntitlements(entitlementRequest);
  }

  @Test
  void validate_negative() {
    var entitlementRequest = entitlementRequest();
    when(entitlementCrudService.getEntitlements(entitlementRequest)).thenReturn(emptyList());

    assertThatThrownBy(() -> validator.validate(entitlementRequest))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage("Entitlements are not found for applications: [test-app-1.0.0]");
  }

  @ParameterizedTest
  @DisplayName("shouldValidate_parameterized")
  @CsvSource({"ENTITLE,false", "REVOKE,true", "UPGRADE,false", ",false"})
  void shouldValidate_parameterized(EntitlementRequestType type, boolean expected) {
    var request = EntitlementRequest.builder().type(type).build();
    var result = validator.shouldValidate(request);
    assertThat(result).isEqualTo(expected);
  }

  private static EntitlementRequest entitlementRequest() {
    return EntitlementRequest.builder()
      .applications(List.of(APPLICATION_ID))
      .tenantId(TENANT_ID)
      .type(REVOKE)
      .build();
  }
}
