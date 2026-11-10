package org.folio.entitlement.it;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.folio.common.utils.OkapiHeaders;
import org.folio.entitlement.configuration.cache.CacheConfiguration;
import org.folio.entitlement.support.KeycloakTestClientConfiguration;
import org.folio.entitlement.support.KeycloakTestClientConfiguration.KeycloakTestClient;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.jwt.openid.OpenidJwtParserProvider;
import org.folio.test.extensions.EnableKeycloakSecurity;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.extensions.KeycloakRealms;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Runs with Keycloak integration enabled, where the tenant and application descriptor caches are active.
 */
@IntegrationTest
@EnableKeycloakTlsMode
@EnableKeycloakSecurity
@KeycloakRealms("/keycloak/test-realm.json")
@Import(KeycloakTestClientConfiguration.class)
@Sql(executionPhase = BEFORE_TEST_METHOD, scripts = "classpath:/sql/folio-entitlement.sql")
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
class EntitlementApplicationServiceCacheIT extends BaseIntegrationTest {

  private static final String APPLICATION_ID = "folio-app2-2.0.0";
  private static final String TEST_TENANT = "test";

  @Autowired private KeycloakTestClient keycloakTestClient;
  @Autowired private CacheManager cacheManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp(@Autowired OpenidJwtParserProvider openidJwtParserProvider) {
    openidJwtParserProvider.invalidateCache();
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get-query-by-name.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json"
  })
  void getApplicationDescriptorsByTenantName_positive_secondCallIsServedFromCaches() throws Exception {
    callEndpoint().andExpect(jsonPath("$.applicationDescriptors[0].id", is(APPLICATION_ID)));
    callEndpoint().andExpect(jsonPath("$.applicationDescriptors[0].id", is(APPLICATION_ID)));

    assertThat(downstreamCalls("/tenants")).isEqualTo(1);
    assertThat(downstreamCalls("/applications")).isEqualTo(1);
    assertThat(cacheManager.getCache(CacheConfiguration.TENANT_ID).get(TEST_TENANT)).isNotNull();
    assertThat(cacheManager.getCache(CacheConfiguration.APPLICATION_DESCRIPTOR).get(APPLICATION_ID)).isNotNull();
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get-query-by-name.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json"
  })
  void getApplicationDescriptorsByTenantName_positive_entitlementsAreNotCached() throws Exception {
    callEndpoint().andExpect(jsonPath("$.totalRecords", is(1)));

    jdbcTemplate.update("delete from entitlement_module");
    jdbcTemplate.update("delete from entitlement");

    callEndpoint()
      .andExpect(jsonPath("$.totalRecords", is(0)))
      .andExpect(jsonPath("$.applicationDescriptors").isEmpty());

    assertThat(downstreamCalls("/applications")).isEqualTo(1);
    // the tenant id is re-resolved once, because no entitlements were found for the cached tenant id
    assertThat(downstreamCalls("/tenants")).isEqualTo(2);
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get-query-by-name.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json"
  })
  void getApplicationDescriptorsByTenantName_positive_tenantRecreatedUnderSameName() throws Exception {
    callEndpoint().andExpect(jsonPath("$.totalRecords", is(1)));

    var recreatedTenantId = UUID.randomUUID();
    getWireMockClient().register(get(urlPathEqualTo("/tenants"))
      .withQueryParam("query", containing("name==" + TEST_TENANT))
      .willReturn(okJson("{\"tenants\":[{\"id\":\"" + recreatedTenantId + "\",\"name\":\"" + TEST_TENANT
        + "\"}],\"totalRecords\":1}")));
    jdbcTemplate.update("delete from entitlement_module");
    jdbcTemplate.update("delete from entitlement");
    jdbcTemplate.update("insert into entitlement(application_id, tenant_id) values (?, ?)",
      APPLICATION_ID, recreatedTenantId);

    callEndpoint()
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.applicationDescriptors[0].id", is(APPLICATION_ID)));

    assertThat(downstreamCalls("/tenants")).isEqualTo(2);
    assertThat(downstreamCalls("/applications")).isEqualTo(1);
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get-query-by-name.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json"
  })
  void getApplicationDescriptorsByTenantName_positive_unknownTenantIsNotFoundAndNotCached() throws Exception {
    var unknownTenant = "unknown";
    getWireMockClient().register(get(urlPathEqualTo("/tenants"))
      .withQueryParam("query", containing("name==" + unknownTenant))
      .willReturn(okJson("{\"tenants\":[],\"totalRecords\":0}")));

    callEndpointFor(unknownTenant, status().isNotFound());
    callEndpointFor(unknownTenant, status().isNotFound());

    assertThat(cacheManager.getCache(CacheConfiguration.TENANT_ID).get(unknownTenant)).isNull();
    assertThat(downstreamCalls("/tenants")).isEqualTo(2);
    assertThat(downstreamCalls("/applications")).isZero();
  }

  private ResultActions callEndpointFor(String tenantName, ResultMatcher expectedStatus) throws Exception {
    return mockMvc.perform(get("/entitlements/{tenantName}/applications", tenantName)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TENANT, TEST_TENANT)
        .header(OkapiHeaders.TOKEN, keycloakTestClient.generateAccessToken(TEST_TENANT)))
      .andExpect(expectedStatus);
  }

  private ResultActions callEndpoint() throws Exception {
    return mockMvc.perform(get("/entitlements/{tenantName}/applications", TEST_TENANT)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TENANT, TEST_TENANT)
        .header(OkapiHeaders.TOKEN, keycloakTestClient.generateAccessToken(TEST_TENANT)))
      .andExpect(status().isOk());
  }

  private int downstreamCalls(String path) throws Exception {
    return getWireMockClient().find(getRequestedFor(urlPathEqualTo(path))).size();
  }
}
