package org.folio.entitlement.it;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.folio.entitlement.service.flow.AsyncConfirmationSweeper;
import org.folio.entitlement.support.base.BaseIntegrationTest;
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
  "application.apigw.enabled=false",
  "application.keycloak.enabled=false",
})
class AsyncConfirmationSweeperIT extends BaseIntegrationTest {

  private static final UUID FLOW_ID = UUID.fromString("aa000000-0000-0000-0000-000000000001");
  private static final String STAGE1_NAME = "CapabilitiesModuleEventPublisher";

  @Autowired
  private AsyncConfirmationSweeper sweeper;

  @Test
  @Sql("classpath:/sql/sweeper/stale-flow.sql")
  void failStaleAsyncFlows_positive() throws Exception {
    sweeper.failStaleAsyncFlows();

    getFlow(FLOW_ID, true)
      .andExpect(jsonPath("$.status", is("failed")))
      .andExpect(jsonPath("$.applicationFlows[0].status", is("failed")))
      .andExpect(jsonPath("$.applicationFlows[0].stages[?(@.name == '" + STAGE1_NAME + "')].status",
        contains("failed")));
  }

  @Test
  @Sql("classpath:/sql/sweeper/recent-flow.sql")
  void failStaleAsyncFlows_positive_recentAnchor_flowNotFailed() throws Exception {
    sweeper.failStaleAsyncFlows();

    getFlow(FLOW_ID, false)
      .andExpect(jsonPath("$.status", is("in_progress")));
  }

  @Test
  @Sql("classpath:/sql/sweeper/no-anchor-flow.sql")
  void failStaleAsyncFlows_positive_noAnchor_flowNotFailed() throws Exception {
    sweeper.failStaleAsyncFlows();

    getFlow(FLOW_ID, false)
      .andExpect(jsonPath("$.status", is("in_progress")));
  }

  private static ResultActions getFlow(UUID flowId, boolean includeStages) throws Exception {
    return mockMvc.perform(get("/entitlement-flows/{flowId}", flowId)
        .header(TOKEN, TestConstants.OKAPI_AUTH_TOKEN)
        .queryParam("includeStages", String.valueOf(includeStages)))
      .andExpect(status().isOk());
  }
}
