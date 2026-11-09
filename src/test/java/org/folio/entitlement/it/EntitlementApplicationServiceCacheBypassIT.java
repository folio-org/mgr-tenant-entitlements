package org.folio.entitlement.it;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.configuration.cache.CacheConfiguration.ACCESS_TOKEN;
import static org.folio.entitlement.configuration.cache.CacheConfiguration.APPLICATION_DESCRIPTOR;
import static org.folio.entitlement.configuration.cache.CacheConfiguration.TENANT_ID;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.folio.common.utils.OkapiHeaders;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Runs with the default integration profile (Keycloak integration disabled), where the caller's token is forwarded
 * to mgr-tenants and mgr-applications. Responses must not be shared between callers, so caches are bypassed.
 */
@IntegrationTest
@Sql(executionPhase = BEFORE_TEST_METHOD, scripts = "classpath:/sql/folio-entitlement.sql")
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
class EntitlementApplicationServiceCacheBypassIT extends BaseIntegrationTest {

  private static final String APPLICATION_ID = "folio-app2-2.0.0";
  private static final String TEST_TENANT = "test";
  private static final String USER_TOKEN = "user-token";

  @Autowired private CacheManager cacheManager;

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get-query-by-name.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json"
  })
  void getApplicationDescriptorsByTenantName_positive_cachesAreBypassedWhenCallerTokenIsForwarded() throws Exception {
    callEndpoint();
    callEndpoint();

    assertThat(downstreamCalls("/tenants")).isEqualTo(2);
    assertThat(downstreamCalls("/applications")).isEqualTo(2);
    assertThat(cacheManager.getCache(TENANT_ID).get(TEST_TENANT)).isNull();
    assertThat(cacheManager.getCache(APPLICATION_DESCRIPTOR).get(APPLICATION_ID)).isNull();
  }

  @Test
  void cacheManager_positive_accessTokenCacheIsAbsentWithoutKeycloak() {
    assertThat(cacheManager.getCacheNames()).containsExactlyInAnyOrder(TENANT_ID, APPLICATION_DESCRIPTOR);
    assertThat(cacheManager.getCache(ACCESS_TOKEN)).isNull();
  }

  private ResultActions callEndpoint() throws Exception {
    return mockMvc.perform(get("/entitlements/{tenantName}/applications", TEST_TENANT)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TENANT, TEST_TENANT)
        .header(OkapiHeaders.TOKEN, USER_TOKEN))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.applicationDescriptors[0].id", is(APPLICATION_ID)));
  }

  private int downstreamCalls(String path) throws Exception {
    return getWireMockClient().find(getRequestedFor(urlPathEqualTo(path))).size();
  }
}
