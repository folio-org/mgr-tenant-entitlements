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
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.entitlement.service.FlowStageService;
import org.folio.entitlement.service.flow.ApplicationFlowService;
import org.folio.entitlement.service.flow.FlowService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@UnitTest
@Import({ControllerTestConfiguration.class, FlowController.class})
@WebMvcTest(FlowController.class)
@TestPropertySource(properties = "application.router.path-prefix=/")
class  FlowControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private FlowService flowService;
  @MockBean private ApplicationFlowService applicationFlowService;
  @MockBean private FlowStageService flowStageService;

  @Test
  void findFlows_positive() throws Exception {
    var query = "type == ENTITLE";
    var flows = SearchResult.of(List.of(flow()));
    when(flowService.find(query, 25, 2)).thenReturn(flows);
    mockMvc.perform(get("/entitlement-flows", FLOW_ID)
        .queryParam("query", query)
        .queryParam("limit", "25")
        .queryParam("offset", "2")
        .header(OkapiHeaders.TOKEN, OKAPI_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.flows[0].id", is(FLOW_ID.toString())))
      .andExpect(jsonPath("$.flows[0].status", is("finished")))
      .andExpect(jsonPath("$.flows[0].applicationFlows[0].id", is(APPLICATION_FLOW_ID.toString())))
      .andExpect(jsonPath("$.flows[0].applicationFlows[0].status", is("finished")))
      .andExpect(jsonPath("$.flows[0].applicationFlows[0].tenantId", is(TENANT_ID.toString())))
      .andExpect(jsonPath("$.flows[0].applicationFlows[0].applicationId", is(APPLICATION_ID)));
  }

  @Test
  void getFlowById_positive() throws Exception {
    when(flowService.getById(FLOW_ID, false)).thenReturn(flow());
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
  void findApplicationFlows_positive() throws Exception {
    var query = "tenantId=" + TENANT_ID;
    var applicationFlows = SearchResult.of(List.of(applicationFlow()));
    when(applicationFlowService.find(query, 25, 2)).thenReturn(applicationFlows);

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
  void getApplicationFlowById_positive() throws Exception {
    when(applicationFlowService.getById(APPLICATION_FLOW_ID, false)).thenReturn(applicationFlow());
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
    when(flowStageService.findByFlowId(APPLICATION_FLOW_ID)).thenReturn(searchResult);
    mockMvc.perform(get("/application-flows/{applicationFlowId}/stages", APPLICATION_FLOW_ID)
        .header(OkapiHeaders.TOKEN, OKAPI_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.stages[0].flowId", is(APPLICATION_FLOW_ID.toString())))
      .andExpect(jsonPath("$.stages[0].status", is("finished")))
      .andExpect(jsonPath("$.stages[0].name", is("OkapiModuleInstaller")));
  }

  @Test
  void getEntitlementStageByName_positive() throws Exception {
    var stageName = "OkapiModuleInstaller";
    when(flowStageService.getEntitlementStage(APPLICATION_FLOW_ID, stageName)).thenReturn(stage());
    mockMvc.perform(get("/application-flows/{applicationFlowId}/stages/{name}", APPLICATION_FLOW_ID, stageName)
        .header(OkapiHeaders.TOKEN, OKAPI_TOKEN)
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.flowId", is(APPLICATION_FLOW_ID.toString())))
      .andExpect(jsonPath("$.status", is("finished")))
      .andExpect(jsonPath("$.name", is(stageName)));
  }

  private static Flow flow() {
    return new Flow().id(FLOW_ID).status(FINISHED).applicationFlows(List.of(applicationFlow()));
  }

  private static ApplicationFlow applicationFlow() {
    return new ApplicationFlow()
      .id(APPLICATION_FLOW_ID)
      .flowId(FLOW_ID)
      .applicationId(APPLICATION_ID)
      .tenantId(TENANT_ID)
      .status(FINISHED);
  }

  private static FlowStage stage() {
    return new FlowStage()
      .name("OkapiModuleInstaller")
      .status(FINISHED)
      .flowId(APPLICATION_FLOW_ID);
  }
}
