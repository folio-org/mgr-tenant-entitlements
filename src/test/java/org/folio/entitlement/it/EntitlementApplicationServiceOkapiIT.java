package org.folio.entitlement.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.test.TestConstants.OKAPI_AUTH_TOKEN;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.folio.common.utils.OkapiHeaders;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.extensions.EnableOkapiSecurity;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@EnableOkapiSecurity
@WireMockStub("/wiremock/mod-authtoken/verify-token-any.json")
@Sql(executionPhase = BEFORE_TEST_METHOD, scripts = "classpath:/sql/folio-entitlement.sql")
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
class EntitlementApplicationServiceOkapiIT extends BaseIntegrationTest {

  private static final String APPLICATION_ID = "folio-app2-2.0.0";
  private static final String APPLICATION_NAME = "folio-app2";
  private static final String APPLICATION_VERSION = "2.0.0";
  private static final String TEST_TENANT = "test";
  private static final String TEST_TENANT2 = "test2";

  @BeforeAll
  static void beforeAll(@Autowired ApplicationContext appContext) {
    assertThat(appContext.containsBean("tokenParser")).isFalse();
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get-query-by-name.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json"
  })
  void getApplicationDescriptorsByTenantName_positive() throws Exception {
    mockMvc.perform(get("/entitlements/{tenantName}/applications", TEST_TENANT)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TENANT, TEST_TENANT)
        .header(TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.applicationDescriptors[0].id", is(APPLICATION_ID)))
      .andExpect(jsonPath("$.applicationDescriptors[0].name", is(APPLICATION_NAME)))
      .andExpect(jsonPath("$.applicationDescriptors[0].version", is(APPLICATION_VERSION)));
  }

  @Test
  @WireMockStub("/wiremock/mgr-tenants/test/get-query-by-name-test2.json")
  void getApplicationDescriptorsByTenantNamePathTenantMissMatch_positive() throws Exception {
    mockMvc.perform(get("/entitlements/{tenantName}/applications", TEST_TENANT2)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TENANT, TEST_TENANT)
        .header(OkapiHeaders.TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk());
  }
}
