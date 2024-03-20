package org.folio.entitlement.it;

import static java.net.URLEncoder.encode;
import static java.net.http.HttpRequest.BodyPublishers.ofString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.folio.entitlement.support.TestConstants.DUMMY_SSL_CONTEXT;
import static org.folio.entitlement.support.TestUtils.OBJECT_MAPPER;
import static org.folio.test.security.TestJwtGenerator.generateJwtToken;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.folio.common.utils.OkapiHeaders;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.security.integration.keycloak.configuration.properties.KeycloakProperties;
import org.folio.test.extensions.EnableKeycloakSecurity;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.extensions.KeycloakRealms;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@EnableKeycloakTlsMode
@IntegrationTest
@EnableKeycloakSecurity
@KeycloakRealms("/keycloak/test-realm.json")
@Sql(executionPhase = BEFORE_TEST_METHOD, scripts = "classpath:/sql/folio-entitlement.sql")
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
class EntitlementApplicationServiceIT extends BaseIntegrationTest {

  private static final String APPLICATION_ID = "folio-app2-2.0.0";
  private static final String APPLICATION_NAME = "folio-app2";
  private static final String APPLICATION_VERSION = "2.0.0";
  private static final String TEST_TENANT = "test";
  private static final String TEST_TENANT2 = "test2";
  private static final Map<String, Entry<String, String>> REALM_CREDENTIALS_MAP = Map.of(
    TEST_TENANT, new SimpleImmutableEntry<>("test-login-application", "test-login-application-secret"),
    TEST_TENANT2, new SimpleImmutableEntry<>("test2-login-application", "test2-login-application-secret"));

  private static HttpClient httpClient;

  @Autowired private KeycloakProperties keycloakProperties;

  @BeforeEach
  @SneakyThrows
  void setUp() {
    httpClient = HttpClient.newBuilder().sslContext(DUMMY_SSL_CONTEXT).build();
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get-query-by-name.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query.json"
  })
  void getApplicationDescriptorsByTenantName_positive() throws Exception {
    mockMvc.perform(get("/entitlements/{tenantName}/applications", TEST_TENANT)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TENANT, TEST_TENANT)
        .header(OkapiHeaders.TOKEN, generateAccessToken(TEST_TENANT)))
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
        .header(OkapiHeaders.TOKEN, generateAccessToken(TEST_TENANT)))
      .andExpect(status().isOk());
  }

  @Test
  @KeycloakRealms("/keycloak/test2-realm.json")
  void getApplicationDescriptorsByTenantName_negative_tokenTenantMissMatch() throws Exception {
    mockMvc.perform(get("/entitlements/{tenantName}/applications", TEST_TENANT)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TENANT, TEST_TENANT)
        .header(OkapiHeaders.TOKEN, generateAccessToken(TEST_TENANT2)))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("X-Okapi-Tenant header is not the same as resolved tenant")))
      .andExpect(jsonPath("$.errors[0].type", is("ForbiddenException")))
      .andExpect(jsonPath("$.errors[0].code", is("auth_error")));
  }

  @Test
  void getApplicationDescriptorsByTenantName_negative_headerTenantMissMatch() throws Exception {
    mockMvc.perform(get("/entitlements/{tenantName}/applications", TEST_TENANT)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TENANT, "another-tenant")
        .header(OkapiHeaders.TOKEN, generateAccessToken(TEST_TENANT)))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("X-Okapi-Tenant header is not the same as resolved tenant")))
      .andExpect(jsonPath("$.errors[0].type", is("ForbiddenException")))
      .andExpect(jsonPath("$.errors[0].code", is("auth_error")));
  }

  @Test
  void getApplicationDescriptorsByTenantName_negative_invalidTokenIssuer() throws Exception {
    var token = generateJwtToken("http://unknown-kc", TEST_TENANT);
    mockMvc.perform(get("/entitlements/{tenantName}/applications", TEST_TENANT)
        .contentType(APPLICATION_JSON)
        .header(OkapiHeaders.TENANT, TEST_TENANT)
        .header(OkapiHeaders.TOKEN, token))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Failed to validate a token")))
      .andExpect(jsonPath("$.errors[0].type", is("NotAuthorizedException")))
      .andExpect(jsonPath("$.errors[0].code", is("auth_error")));
  }

  @SneakyThrows
  private String generateAccessToken(String tenant) {
    var credentials = requireNonNull(REALM_CREDENTIALS_MAP.get(tenant));
    var tokenRequestBody = Map.of(
      "client_id", credentials.getKey(),
      "client_secret", credentials.getValue(),
      "grant_type", "client_credentials");

    var keycloakBaseUrl = StringUtils.removeEnd(keycloakProperties.getUrl(), "/");
    var uri = URI.create(String.format("%s/realms/%s/protocol/openid-connect/token", keycloakBaseUrl, tenant));
    var request = HttpRequest.newBuilder(uri)
      .method(POST.name(), ofString(toFormUrlencodedValue(tokenRequestBody), UTF_8))
      .header("Content-Type", APPLICATION_FORM_URLENCODED_VALUE)
      .build();

    var response = httpClient.send(request, BodyHandlers.ofString(UTF_8));
    var keycloakTokenJson = OBJECT_MAPPER.readTree(response.body());
    return keycloakTokenJson.path("access_token").asText();
  }

  private static String toFormUrlencodedValue(Map<String, String> params) {
    return params.entrySet()
      .stream()
      .map(entry -> String.format("%s=%s", encode(entry.getKey(), UTF_8), encode(entry.getValue(), UTF_8)))
      .collect(Collectors.joining("&"));
  }
}

