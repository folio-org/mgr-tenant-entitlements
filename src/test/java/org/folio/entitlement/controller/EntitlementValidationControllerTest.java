package org.folio.entitlement.controller;

import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.test.TestUtils.asJsonString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doNothing;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.folio.common.utils.OkapiHeaders;
import org.folio.entitlement.controller.converter.EntitlementTypeConverters;
import org.folio.entitlement.domain.dto.EntitlementRequestBody;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.EntitlementValidationService;
import org.folio.entitlement.service.flow.EntitlementFlowService;
import org.folio.security.integration.keycloak.client.KeycloakAuthClient;
import org.folio.test.extensions.EnableKeycloakSecurity;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@Import({ControllerTestConfiguration.class,
  EntitlementValidationController.class,
  EntitlementTypeConverters.FromString.class})
@MockBean(EntitlementFlowService.class)
@MockBean(KeycloakAuthClient.class)
@WebMvcTest(EntitlementValidationController.class)
@EnableKeycloakSecurity
class EntitlementValidationControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private EntitlementValidationService validationService;

  @ParameterizedTest
  @EnumSource(EntitlementType.class)
  void validate_positive(EntitlementType type) throws Exception {
    var requestBody = new EntitlementRequestBody().tenantId(TENANT_ID).applications(List.of(APPLICATION_ID));

    doNothing().when(validationService).validate(entitlementRequest(type));

    mockMvc.perform(post("/entitlements/validate")
        .header(OkapiHeaders.TOKEN, OKAPI_TOKEN)
        .queryParam("entitlementType", type.getValue())
        .content(asJsonString(requestBody))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
  }

  @Test
  void validate_positive_withValidatorName() throws Exception {
    String validator = "testValidator";
    var requestBody = new EntitlementRequestBody().tenantId(TENANT_ID).applications(List.of(APPLICATION_ID));

    doNothing().when(validationService).validateBy(validator, entitlementRequest());

    mockMvc.perform(post("/entitlements/validate")
        .header(OkapiHeaders.TOKEN, OKAPI_TOKEN)
        .queryParam("entitlementType", ENTITLE.getValue())
        .queryParam("validator", validator)
        .content(asJsonString(requestBody))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
  }

  @Test
  void validate_negative_invalidEntitleType() throws Exception {
    var requestBody = new EntitlementRequestBody().tenantId(TENANT_ID).applications(List.of(APPLICATION_ID));

    String invalidType = "invalidType";
    mockMvc.perform(post("/entitlements/validate")
        .header(OkapiHeaders.TOKEN, OKAPI_TOKEN)
        .queryParam("entitlementType", invalidType)
        .content(asJsonString(requestBody))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", containsString("Failed to convert value")))
      .andExpect(jsonPath("$.errors[0].message", containsString("for value '" + invalidType + "'")))
      .andExpect(jsonPath("$.errors[0].type", is("MethodArgumentTypeMismatchException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  private static EntitlementRequest entitlementRequest() {
    return entitlementRequest(ENTITLE);
  }

  private static EntitlementRequest entitlementRequest(EntitlementType type) {
    return EntitlementRequest.builder()
      .type(type)
      .tenantId(TENANT_ID)
      .applications(List.of(APPLICATION_ID))
      .okapiToken(OKAPI_TOKEN)
      .build();
  }
}
