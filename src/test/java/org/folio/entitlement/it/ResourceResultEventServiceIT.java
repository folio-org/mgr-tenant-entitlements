package org.folio.entitlement.it;

import static org.folio.integration.kafka.model.ResourceResultStatus.FAILURE;
import static org.folio.integration.kafka.model.ResourceResultStatus.SUCCESS;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.folio.entitlement.integration.kafka.ResourceResultEventService;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.folio.integration.kafka.model.ResourceResultStatus;
import org.folio.test.TestConstants;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
@SqlMergeMode(MERGE)
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
@TestPropertySource(properties = {
  "application.kong.enabled=false",
  "application.keycloak.enabled=false",
})
class ResourceResultEventServiceIT extends BaseIntegrationTest {

  private static final UUID FLOW_ID = UUID.fromString("aa000000-0000-0000-0000-000000000001");
  private static final UUID AF1_ID = UUID.fromString("bb000000-0000-0000-0000-000000000001");
  private static final UUID AF2_ID = UUID.fromString("bb000000-0000-0000-0000-000000000002");
  private static final UUID S1_ID = UUID.fromString("cc000000-0000-0000-0000-000000000001");

  private static final String STAGE1_NAME = "CapabilitiesModuleEventPublisher";
  private static final String STAGE2_NAME = "SystemUserModuleEventPublisher";
  private static final String TENANT_NAME = "test";

  @Autowired
  private ResourceResultEventService resourceResultEventService;

  @Test
  @Sql("classpath:/sql/resource-result-event/single-stage-in-progress.sql")
  void processEvent_positive_successResult_singleStage() throws Exception {
    resourceResultEventService.processEvent(event(S1_ID, SUCCESS, null));

    getFlow(FLOW_ID, true)
      .andExpect(jsonPath("$.status", is("finished")))
      .andExpect(jsonPath("$.applicationFlows[0].status", is("finished")))
      .andExpect(jsonPath(stageStatusPath(STAGE1_NAME), contains("finished")));
  }

  @Test
  @Sql("classpath:/sql/resource-result-event/single-stage-in-progress.sql")
  void processEvent_positive_failureResult_singleStage() throws Exception {
    resourceResultEventService.processEvent(event(S1_ID, FAILURE, "stage-error-details"));

    getFlow(FLOW_ID, true)
      .andExpect(jsonPath("$.status", is("failed")))
      .andExpect(jsonPath("$.applicationFlows[0].status", is("failed")))
      .andExpect(jsonPath(stageStatusPath(STAGE1_NAME), contains("failed")))
      .andExpect(jsonPath(stageErrorPath(STAGE1_NAME), contains("stage-error-details")));
  }

  @Test
  @Sql("classpath:/sql/resource-result-event/two-stages-in-progress.sql")
  void processEvent_positive_successResult_oneOfTwoStages_flowStaysInProgress() throws Exception {
    resourceResultEventService.processEvent(event(S1_ID, SUCCESS, null));

    getFlow(FLOW_ID, true)
      .andExpect(jsonPath("$.status", is("in_progress")))
      .andExpect(jsonPath("$.applicationFlows[0].status", is("in_progress")))
      .andExpect(jsonPath(stageStatusPath(STAGE1_NAME), contains("finished")))
      .andExpect(jsonPath(stageStatusPath(STAGE2_NAME), contains("in_progress")));
  }

  @Test
  @Sql("classpath:/sql/resource-result-event/two-appflows-in-progress.sql")
  void processEvent_positive_twoAppFlows_firstAppFlowFinishes_topLevelFlowStaysInProgress()
    throws Exception {
    resourceResultEventService.processEvent(event(S1_ID, SUCCESS, null));

    getFlow(FLOW_ID, false)
      .andExpect(jsonPath("$.status", is("in_progress")))
      .andExpect(jsonPath(appFlowStatusPath(AF1_ID), contains("finished")))
      .andExpect(jsonPath(appFlowStatusPath(AF2_ID), contains("in_progress")));
  }

  @Test
  @Sql("classpath:/sql/resource-result-event/single-stage-already-finished.sql")
  void processEvent_positive_stageAlreadyFinished_eventIgnored() throws Exception {
    resourceResultEventService.processEvent(event(S1_ID, SUCCESS, null));

    getFlow(FLOW_ID, true)
      .andExpect(jsonPath("$.status", is("finished")))
      .andExpect(jsonPath("$.applicationFlows[0].status", is("finished")))
      .andExpect(jsonPath(stageStatusPath(STAGE1_NAME), contains("finished")));
  }

  @Test
  @Sql("classpath:/sql/resource-result-event/single-stage-already-failed.sql")
  void processEvent_positive_stageAlreadyFailed_eventIgnored() throws Exception {
    resourceResultEventService.processEvent(event(S1_ID, SUCCESS, null));

    getFlow(FLOW_ID, true)
      .andExpect(jsonPath("$.status", is("failed")))
      .andExpect(jsonPath("$.applicationFlows[0].status", is("failed")))
      .andExpect(jsonPath(stageStatusPath(STAGE1_NAME), contains("failed")))
      .andExpect(jsonPath(stageErrorPath(STAGE1_NAME), contains("original-error")));
  }

  @Test
  void processEvent_positive_stageNotFound_noException() {
    var unknownStageId = UUID.fromString("ff000000-0000-0000-0000-000000000001");

    Assertions.assertThatNoException()
      .isThrownBy(() -> resourceResultEventService.processEvent(event(unknownStageId, SUCCESS, null)));
  }

  private static ResultActions getFlow(UUID flowId, boolean includeStages) throws Exception {
    return mockMvc.perform(get("/entitlement-flows/{flowId}", flowId)
        .header(TOKEN, TestConstants.OKAPI_AUTH_TOKEN)
        .queryParam("includeStages", String.valueOf(includeStages)))
      .andExpect(status().isOk());
  }

  private static ResourceResultEvent event(UUID stageId, ResourceResultStatus status, String details) {
    return ResourceResultEvent.builder()
      .id(stageId.toString())
      .tenant(TENANT_NAME)
      .status(status)
      .details(details)
      .build();
  }

  private static String stageStatusPath(String stageName) {
    return "$.applicationFlows[0].stages[?(@.name == '" + stageName + "')].status";
  }

  private static String stageErrorPath(String stageName) {
    return "$.applicationFlows[0].stages[?(@.name == '" + stageName + "')].errorMessage";
  }

  private static String appFlowStatusPath(UUID appFlowId) {
    return "$.applicationFlows[?(@.id == '" + appFlowId + "')].status";
  }
}
