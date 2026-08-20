package org.folio.entitlement.it;

import static java.util.concurrent.TimeUnit.SECONDS;
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
import org.awaitility.Awaitility;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.folio.integration.kafka.model.ResourceResultStatus;
import org.folio.test.TestConstants;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
@SqlMergeMode(MERGE)
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
@TestPropertySource(properties = {
  "application.environment=folio",
  "application.kong.enabled=false",
  "application.keycloak.enabled=false",
})
class KafkaMessageListenerIT extends BaseIntegrationTest {

  private static final String TOPIC = "folio.resource-result";

  private static final UUID FLOW_ID = UUID.fromString("aa000000-0000-0000-0000-000000000001");
  private static final UUID S1_ID = UUID.fromString("cc000000-0000-0000-0000-000000000001");

  private static final String STAGE1_NAME = "CapabilitiesModuleEventPublisher";
  private static final String TENANT_NAME = "test";

  @Autowired
  private KafkaTemplate<String, Object> kafkaTemplate;

  @Test
  @Sql("classpath:/sql/resource-result-event/single-stage-in-progress.sql")
  void handleResourceResultEvent_positive_successResult() {
    kafkaTemplate.send(TOPIC, TENANT_NAME, event(S1_ID, SUCCESS, null));

    Awaitility.await().atMost(30, SECONDS).untilAsserted(() ->
      getFlow(FLOW_ID, true)
        .andExpect(jsonPath("$.status", is("finished")))
        .andExpect(jsonPath("$.applicationFlows[0].status", is("finished")))
        .andExpect(jsonPath("$.applicationFlows[0].stages[?(@.name == '" + STAGE1_NAME + "')].status",
          contains("finished")))
    );
  }

  @Test
  @Sql("classpath:/sql/resource-result-event/single-stage-in-progress.sql")
  void handleResourceResultEvent_positive_failureResult() {
    kafkaTemplate.send(TOPIC, TENANT_NAME, event(S1_ID, FAILURE, "kafka-error-details"));

    Awaitility.await().atMost(30, SECONDS).untilAsserted(() ->
      getFlow(FLOW_ID, true)
        .andExpect(jsonPath("$.status", is("failed")))
        .andExpect(jsonPath("$.applicationFlows[0].status", is("failed")))
        .andExpect(jsonPath("$.applicationFlows[0].stages[?(@.name == '" + STAGE1_NAME + "')].status",
          contains("failed")))
        .andExpect(jsonPath("$.applicationFlows[0].stages[?(@.name == '" + STAGE1_NAME + "')].errorMessage",
          contains("kafka-error-details")))
    );
  }

  @Test
  @Sql("classpath:/sql/resource-result-event/single-stage-in-progress.sql")
  void handleResourceResultEvent_positive_stageNotFound_flowStaysInProgress() {
    var unknownStageId = UUID.fromString("ff000000-0000-0000-0000-000000000001");
    kafkaTemplate.send(TOPIC, TENANT_NAME, event(unknownStageId, SUCCESS, null));

    Awaitility.await()
      .during(3, SECONDS)
      .atMost(5, SECONDS)
      .untilAsserted(() ->
        getFlow(FLOW_ID, false)
          .andExpect(jsonPath("$.status", is("in_progress")))
          .andExpect(jsonPath("$.applicationFlows[0].status", is("in_progress")))
      );
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
}
