package org.folio.entitlement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.common.utils.OkapiHeaders.MODULE_ID;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.domain.model.ResultList.asSinglePage;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.IGNORE_ERRORS;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.PURGE;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_PARAMETERS;
import static org.folio.entitlement.support.TestUtils.asJsonString;
import static org.folio.entitlement.support.TestUtils.parseResponse;
import static org.folio.test.TestConstants.OKAPI_AUTH_TOKEN;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_XML;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import org.folio.common.utils.OkapiHeaders;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.dto.EntitlementRequestBody;
import org.folio.entitlement.domain.dto.ExtendedEntitlements;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.EntitlementService;
import org.folio.entitlement.service.FlowStageService;
import org.folio.security.exception.NotAuthorizedException;
import org.folio.security.integration.keycloak.client.KeycloakAuthClient;
import org.folio.test.extensions.EnableKeycloakSecurity;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@Import({ControllerTestConfiguration.class, EntitlementController.class})
@MockBean(FlowStageService.class)
@WebMvcTest(EntitlementController.class)
@EnableKeycloakSecurity
@TestPropertySource(properties = "application.router.path-prefix=/")
class EntitlementControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private EntitlementService entitlementService;
  @MockBean private KeycloakAuthClient authClient;

  @Test
  void create_positive() throws Exception {
    var requestBody = new EntitlementRequestBody().tenantId(TENANT_ID).applications(List.of(APPLICATION_ID));
    var expectedEntitlements = entitlements();
    when(entitlementService.performRequest(entitlementRequest())).thenReturn(expectedEntitlements);

    var mvcResult = mockMvc.perform(post("/entitlements")
        .header(OkapiHeaders.TOKEN, OKAPI_TOKEN)
        .queryParam("tenantParameters", TENANT_PARAMETERS)
        .queryParam("purge", String.valueOf(PURGE))
        .queryParam("ignoreErrors", String.valueOf(IGNORE_ERRORS))
        .content(asJsonString(requestBody))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isCreated())
      .andReturn();

    var actual = parseResponse(mvcResult, new TypeReference<ExtendedEntitlements>() {});
    assertThat(actual).isEqualTo(expectedEntitlements);
  }

  @Test
  void get_positive() throws Exception {
    var cqlQuery = String.format("tenantId=%s", TENANT_ID);
    when(entitlementService.findByQuery(cqlQuery, false, 10, 0)).thenReturn(asSinglePage(entitlement()));

    mockMvc.perform(get("/entitlements")
        .param("query", cqlQuery)
        .param("limit", "10")
        .param("offset", "0")
        .header(OkapiHeaders.TOKEN, OKAPI_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.entitlements[0].applicationId", is(APPLICATION_ID)))
      .andExpect(jsonPath("$.entitlements[0].tenantId", is(TENANT_ID.toString())));
  }

  @Test
  void get_positive_includeModules() throws Exception {
    var cqlQuery = String.format("tenantId=%s", TENANT_ID);
    when(entitlementService.findByQuery(cqlQuery, true, 10, 0)).thenReturn(asSinglePage(entitlementWithModules()));

    mockMvc.perform(get("/entitlements")
        .param("query", cqlQuery)
        .param("includeModules", "true")
        .param("limit", "10")
        .param("offset", "0")
        .header(OkapiHeaders.TOKEN, OKAPI_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.entitlements[0].applicationId", is(APPLICATION_ID)))
      .andExpect(jsonPath("$.entitlements[0].tenantId", is(TENANT_ID.toString())))
      .andExpect(jsonPath("$.entitlements[0].modules[0]", is(MODULE_ID)));
  }

  @Test
  void delete_positive() throws Exception {
    var request = new EntitlementRequestBody().tenantId(TENANT_ID).applications(List.of(APPLICATION_ID));
    var expectedEntitlements = entitlements();
    when(entitlementService.performRequest(revokeRequest())).thenReturn(expectedEntitlements);

    var mvcResult = mockMvc.perform(delete("/entitlements")
        .header(OkapiHeaders.TOKEN, OKAPI_TOKEN)
        .queryParam("tenantParameters", TENANT_PARAMETERS)
        .queryParam("purge", String.valueOf(PURGE))
        .queryParam("ignoreErrors", String.valueOf(IGNORE_ERRORS))
        .content(asJsonString(request))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andReturn();

    var actual = parseResponse(mvcResult, new TypeReference<ExtendedEntitlements>() {});
    assertThat(actual).isEqualTo(expectedEntitlements);
  }

  @Test
  void delete_negative_requestWithoutBody() throws Exception {
    mockMvc.perform(delete("/entitlements", TENANT_ID)
        .queryParam("tenantParameters", TENANT_PARAMETERS)
        .queryParam("purge", String.valueOf(PURGE))
        .queryParam("ignoreErrors", String.valueOf(IGNORE_ERRORS))
        .header(OkapiHeaders.TOKEN, OKAPI_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", containsString("Required request body is missing")))
      .andExpect(jsonPath("$.errors[0].type", is("HttpMessageNotReadableException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void delete_negative_unsupportedContentType() throws Exception {
    mockMvc.perform(delete("/entitlements", TENANT_ID)
        .header(OkapiHeaders.TOKEN, OKAPI_TOKEN)
        .contentType(APPLICATION_XML))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Content-Type 'application/xml' is not supported")))
      .andExpect(jsonPath("$.errors[0].type", is("HttpMediaTypeNotSupportedException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void delete_negative_unauthorized() throws Exception {
    when(entitlementService.performRequest(entitlementRequest())).thenReturn(entitlements());
    when(authClient.evaluatePermissions(anyMap(), anyString())).thenThrow(new NotAuthorizedException("test"));

    mockMvc.perform(delete("/entitlements")
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void delete_negative_noAuthToken() throws Exception {
    when(entitlementService.performRequest(entitlementRequest())).thenReturn(entitlements());

    mockMvc.perform(delete("/entitlements")
        .contentType(APPLICATION_JSON))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void upgrade_positive() throws Exception {
    var requestBody = new EntitlementRequestBody().tenantId(TENANT_ID).applications(List.of(APPLICATION_ID));
    var expectedEntitlements = entitlements();
    when(entitlementService.performRequest(upgradeRequest())).thenReturn(expectedEntitlements);

    mockMvc.perform(put("/entitlements")
        .header(OkapiHeaders.TOKEN, OKAPI_TOKEN)
        .queryParam("tenantParameters", TENANT_PARAMETERS)
        .queryParam("purge", String.valueOf(PURGE))
        .queryParam("ignoreErrors", String.valueOf(IGNORE_ERRORS))
        .content(asJsonString(requestBody))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(expectedEntitlements)));
  }

  private static Entitlement entitlement() {
    return new Entitlement().applicationId(APPLICATION_ID).tenantId(TENANT_ID);
  }

  private static Entitlement entitlementWithModules() {
    return new Entitlement().applicationId(APPLICATION_ID).tenantId(TENANT_ID).addModulesItem(MODULE_ID);
  }

  private static EntitlementRequest entitlementRequest() {
    return EntitlementRequest.builder()
      .type(ENTITLE)
      .tenantId(TENANT_ID)
      .purge(false)
      .async(false)
      .tenantParameters(TENANT_PARAMETERS)
      .ignoreErrors(IGNORE_ERRORS)
      .applications(List.of(APPLICATION_ID))
      .okapiToken(OKAPI_TOKEN)
      .build();
  }

  private static EntitlementRequest revokeRequest() {
    return EntitlementRequest.builder()
      .type(REVOKE)
      .tenantId(TENANT_ID)
      .purge(PURGE)
      .async(false)
      .tenantParameters(TENANT_PARAMETERS)
      .ignoreErrors(true)
      .applications(List.of(APPLICATION_ID))
      .okapiToken(OKAPI_TOKEN)
      .build();
  }

  private static EntitlementRequest upgradeRequest() {
    return EntitlementRequest.builder()
      .type(UPGRADE)
      .tenantId(TENANT_ID)
      .purge(false)
      .async(false)
      .tenantParameters(TENANT_PARAMETERS)
      .ignoreErrors(true)
      .applications(List.of(APPLICATION_ID))
      .okapiToken(OKAPI_TOKEN)
      .build();
  }

  private static ExtendedEntitlements entitlements() {
    return new ExtendedEntitlements().totalRecords(1).flowId(FLOW_ID).addEntitlementsItem(
      new Entitlement().applicationId(APPLICATION_ID).tenantId(TENANT_ID));
  }
}
