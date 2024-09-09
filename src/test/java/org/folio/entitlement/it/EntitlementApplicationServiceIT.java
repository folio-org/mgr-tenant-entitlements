package org.folio.entitlement.it;

import static org.folio.test.security.TestJwtGenerator.generateJwtToken;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.folio.common.utils.OkapiHeaders;
import org.folio.entitlement.support.KeycloakTestClientConfiguration;
import org.folio.entitlement.support.KeycloakTestClientConfiguration.KeycloakTestClient;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.extensions.EnableKeycloakSecurity;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.extensions.KeycloakRealms;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@EnableKeycloakTlsMode
@EnableKeycloakSecurity
@KeycloakRealms("/keycloak/test-realm.json")
@Import(KeycloakTestClientConfiguration.class)
@Sql(executionPhase = BEFORE_TEST_METHOD, scripts = "classpath:/sql/folio-entitlement.sql")
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
class EntitlementApplicationServiceIT extends BaseIntegrationTest {

  private static final String APPLICATION_ID = "folio-app2-2.0.0";
  private static final String APPLICATION_NAME = "folio-app2";
  private static final String APPLICATION_VERSION = "2.0.0";
  private static final String TEST_TENANT = "test";
  private static final String TEST_TENANT2 = "test2";

  @Autowired private KeycloakTestClient keycloakTestClient;

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get-query-by-name.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json"
  })
  void getApplicationDescriptorsByTenantName_positive() throws Exception {
    mockMvc.perform(get("/entitlements/{tenantName}/applications", TEST_TENANT)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TENANT, TEST_TENANT)
        .header(OkapiHeaders.TOKEN, keycloakTestClient.generateAccessToken(TEST_TENANT)))
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
        .header(OkapiHeaders.TOKEN, keycloakTestClient.generateAccessToken(TEST_TENANT)))
      .andExpect(status().isOk());
  }

  @Test
  @KeycloakRealms("/keycloak/test2-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get-query-by-name.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json"
  })
  void getApplicationDescriptorsByTenantName_negative_tokenTenantMissMatch() throws Exception {
    mockMvc.perform(get("/entitlements/{tenantName}/applications", TEST_TENANT)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TENANT, TEST_TENANT)
        .header(OkapiHeaders.TOKEN, keycloakTestClient.generateAccessToken(TEST_TENANT2)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.applicationDescriptors[0].id", is(APPLICATION_ID)))
      .andExpect(jsonPath("$.applicationDescriptors[0].name", is(APPLICATION_NAME)))
      .andExpect(jsonPath("$.applicationDescriptors[0].version", is(APPLICATION_VERSION)));
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get-query-by-name.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json"
  })
  void getApplicationDescriptorsByTenantName_negative_headerTenantMissMatch() throws Exception {
    mockMvc.perform(get("/entitlements/{tenantName}/applications", TEST_TENANT)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TENANT, "another-tenant")
        .header(OkapiHeaders.TOKEN, keycloakTestClient.generateAccessToken(TEST_TENANT)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.applicationDescriptors[0].id", is(APPLICATION_ID)))
      .andExpect(jsonPath("$.applicationDescriptors[0].name", is(APPLICATION_NAME)))
      .andExpect(jsonPath("$.applicationDescriptors[0].version", is(APPLICATION_VERSION)));
  }

  @Test
  void getApplicationDescriptorsByTenantName_negative_invalidTokenIssuer() throws Exception {
    var token = generateJwtToken("http://unknown-kc", TEST_TENANT);
    mockMvc.perform(get("/entitlements/{tenantName}/applications", TEST_TENANT)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TENANT, TEST_TENANT)
        .header(OkapiHeaders.TOKEN, token))
      .andExpect(status().isUnauthorized());
  }
}
