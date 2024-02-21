package org.folio.entitlement.controller;

import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.folio.common.domain.model.SearchResult;
import org.folio.common.utils.OkapiHeaders;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.EntitlementFlow;
import org.folio.entitlement.domain.dto.EntitlementStage;
import org.folio.entitlement.service.EntitlementStageService;
import org.folio.entitlement.service.flow.EntitlementFlowService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@Import({ControllerTestConfiguration.class, EntitlementFlowController.class})
@WebMvcTest(EntitlementFlowController.class)
@TestPropertySource(properties = "application.router.path-prefix=/")
class EntitlementFlowControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private EntitlementFlowService entitlementFlowService;
  @MockBean private EntitlementStageService entitlementStageService;

  @Test
  void findApplicationFlows_positive() throws Exception {
    var query = "tenantId=" + TENANT_ID;
    when(entitlementFlowService.find(query, 25, 2)).thenReturn(SearchResult.of(List.of(applicationFlow())));
    mockMvc.perform(get("/application-flows", FLOW_ID)
        .queryParam("query", query)
        .queryParam("limit", "25")
        .queryParam("offset", "2")
        .header(OkapiHeaders.TOKEN, OKAPI_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.applicationFlows[0].id", is(APPLICATION_FLOW_ID.toString())))
      .andExpect(jsonPath("$.applicationFlows[0].status", is("finished")))
      .andExpect(jsonPath("$.applicationFlows[0].tenantId", is(TENANT_ID.toString())))
      .andExpect(jsonPath("$.applicationFlows[0].applicationId", is(APPLICATION_ID)));
  }

  @Test
  void getEntitlementFlowById_positive() throws Exception {
    when(entitlementFlowService.findById(FLOW_ID, false)).thenReturn(entitlementFlow());
    mockMvc.perform(get("/entitlement-flows/{flowId}", FLOW_ID)
        .header(OkapiHeaders.TOKEN, OKAPI_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is(FLOW_ID.toString())))
      .andExpect(jsonPath("$.status", is("finished")))
      .andExpect(jsonPath("$.applicationFlows[0].id", is(APPLICATION_FLOW_ID.toString())))
      .andExpect(jsonPath("$.applicationFlows[0].status", is("finished")))
      .andExpect(jsonPath("$.applicationFlows[0].tenantId", is(TENANT_ID.toString())))
      .andExpect(jsonPath("$.applicationFlows[0].applicationId", is(APPLICATION_ID)));
  }

  @Test
  void getApplicationFlowById_positive() throws Exception {
    when(entitlementFlowService.findByApplicationFlowId(APPLICATION_FLOW_ID, false)).thenReturn(applicationFlow());
    mockMvc.perform(get("/application-flows/{applicationFlowId}", APPLICATION_FLOW_ID)
        .header(OkapiHeaders.TOKEN, OKAPI_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is(APPLICATION_FLOW_ID.toString())))
      .andExpect(jsonPath("$.status", is("finished")))
      .andExpect(jsonPath("$.tenantId", is(TENANT_ID.toString())))
      .andExpect(jsonPath("$.applicationId", is(APPLICATION_ID)));
  }

  @Test
  void findEntitlementStages_positive() throws Exception {
    var searchResult = SearchResult.of(List.of(stage()));
    when(entitlementStageService.findEntitlementStages(APPLICATION_FLOW_ID)).thenReturn(searchResult);
    mockMvc.perform(get("/application-flows/{applicationFlowId}/stages", APPLICATION_FLOW_ID)
        .header(OkapiHeaders.TOKEN, OKAPI_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.entitlementStages[0].applicationFlowId", is(APPLICATION_FLOW_ID.toString())))
      .andExpect(jsonPath("$.entitlementStages[0].status", is("finished")))
      .andExpect(jsonPath("$.entitlementStages[0].name", is("OkapiModuleInstaller")));
  }

  @Test
  void getEntitlementStageByName_positive() throws Exception {
    var stageName = "OkapiModuleInstaller";
    when(entitlementStageService.getEntitlementStage(APPLICATION_FLOW_ID, stageName)).thenReturn(stage());
    mockMvc.perform(get("/application-flows/{applicationFlowId}/stages/{name}", APPLICATION_FLOW_ID, stageName)
        .header(OkapiHeaders.TOKEN, OKAPI_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.applicationFlowId", is(APPLICATION_FLOW_ID.toString())))
      .andExpect(jsonPath("$.status", is("finished")))
      .andExpect(jsonPath("$.name", is(stageName)));
  }

  private static EntitlementFlow entitlementFlow() {
    return new EntitlementFlow().id(FLOW_ID).status(FINISHED).applicationFlows(List.of(applicationFlow()));
  }

  private static ApplicationFlow applicationFlow() {
    return new ApplicationFlow()
      .id(APPLICATION_FLOW_ID)
      .flowId(FLOW_ID)
      .applicationId(APPLICATION_ID)
      .tenantId(TENANT_ID)
      .status(FINISHED);
  }

  private static EntitlementStage stage() {
    return new EntitlementStage()
      .name("OkapiModuleInstaller")
      .status(FINISHED)
      .applicationFlowId(APPLICATION_FLOW_ID);
  }
}
