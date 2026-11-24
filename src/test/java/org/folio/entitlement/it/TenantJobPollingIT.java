package org.folio.entitlement.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.support.KafkaEventAssertions.assertEntitlementEvents;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.entitlementTopic;
import static org.folio.entitlement.support.TestUtils.asJsonString;
import static org.folio.entitlement.support.TestValues.emptyEntitlements;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.folio.entitlement.support.TestValues.entitlementEvent;
import static org.folio.entitlement.support.TestValues.entitlementRequest;
import static org.folio.entitlement.support.TestValues.entitlementWithModules;
import static org.folio.entitlement.support.TestValues.entitlements;
import static org.folio.entitlement.support.TestValues.extendedEntitlements;
import static org.folio.entitlement.support.TestValues.queryByTenantAndAppId;
import static org.folio.entitlement.support.UpgradeTestValues.FOLIO_APP6_V1_ID;
import static org.folio.entitlement.support.UpgradeTestValues.FOLIO_APP6_V2_ID;
import static org.folio.entitlement.utils.IntegrationTestUtil.getFlowStage;
import static org.folio.entitlement.utils.TenantApiUtils.EGRESS_USE_GATEWAY_HEADER;
import static org.folio.entitlement.utils.TenantApiUtils.EGRESS_USE_GATEWAY_VALUE;
import static org.folio.test.FakeKafkaConsumer.getEvents;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.extensions.KeycloakRealms;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@EnableKeycloakTlsMode
@IntegrationTest
@SqlMergeMode(MERGE)
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
@TestPropertySource(properties = {
  "application.keycloak.enabled=false",
  "application.apigw.enabled=false",
  "application.clients.folio.connect-timeout=250ms",
  "application.clients.folio.read-timeout=1s",
  "application.clients.folio.tenant-job.timeout=3s",
  "application.clients.folio.tenant-job.wait=100ms",
  "application.clients.folio.tenant-job.delay=20ms",
  "application.clients.folio.tenant-job.max-delay=50ms",
  "retries.module.backoff.delay=1",
  "retries.module.backoff.multiplier=1"
})
class TenantJobPollingIT extends BaseIntegrationTest {

  private static final Pattern FAILED_OR_CANCELLED_FLOW =
    Pattern.compile(".*Flow '([A-Za-z0-9\\-]+)' finished with status: (FAILED|CANCELLED).*", Pattern.DOTALL);
  private static final String FOLIO_APP1_ID = "folio-app1-1.0.0";
  private static final String INSTALLER_STAGE = "folio-module1-1.0.0-folioModuleInstaller";
  private static final String TENANT_ENDPOINT = "/folio-module1/_/tenant";
  private static final String JOB_PATH = TENANT_ENDPOINT + "/6a3b3c1e-1111-4222-8333-444455556666";
  private static final String FOLIO_APP2_ID = "folio-app2-2.0.0";
  private static final String UNINSTALLER_STAGE = "folio-module2-2.0.0-folioModuleUninstaller";
  private static final String MODULE2_TENANT_ENDPOINT = "/folio-module2/_/tenant";
  private static final String MODULE2_JOB_PATH = MODULE2_TENANT_ENDPOINT + "/6a3b3c1e-1111-4222-8333-444455556666";
  private static final String MODULE3_TENANT_ENDPOINT = "/folio-module3/_/tenant";
  private static final String MODULE3_JOB_PATH = MODULE3_TENANT_ENDPOINT + "/6a3b3c1e-1111-4222-8333-444455556666";
  private static final String MODULE21_TENANT_ENDPOINT = "/folio-module21/_/tenant";
  private static final String MODULE21_JOB_PATH = MODULE21_TENANT_ENDPOINT + "/6a3b3c1e-1111-4222-8333-444455556666";

  @BeforeAll
  static void beforeAll() {
    fakeKafkaConsumer.registerTopic(entitlementTopic(), EntitlementEvent.class);
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install-async.json",
    "/wiremock/folio-module1/tenant-job-pending.json",
    "/wiremock/folio-module1/tenant-job-pending-2.json",
    "/wiremock/folio-module1/tenant-job-complete.json"
  })
  void install_positive_asyncJobCompletes() throws Exception {
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
    entitleApplications(entitlementRequest(FOLIO_APP1_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_ID)));

    var expected = entitlements(entitlementWithModules(TENANT_ID, FOLIO_APP1_ID, List.of(FOLIO_MODULE1_ID)));
    assertEntitlementsWithModules(queryByTenantAndAppId(FOLIO_APP1_ID), expected);
    assertModuleEntitlements(FOLIO_MODULE1_ID, entitlements(entitlement(TENANT_ID, FOLIO_APP1_ID)));
    assertEntitlementEvents(entitlementEvent(ENTITLE, FOLIO_MODULE1_ID));
    assertThat(requestsTo("GET", JOB_PATH)).isEqualTo(3);
    assertEgressUseGatewayHeaderOnlyOnTenantApiCalls(4);
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json"
  })
  void install_positive_syncModuleIsNotPolled() throws Exception {
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
    entitleApplications(entitlementRequest(FOLIO_APP1_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_ID)));

    assertEntitlementEvents(entitlementEvent(ENTITLE, FOLIO_MODULE1_ID));
    assertThat(requestsTo("POST", TENANT_ENDPOINT)).isEqualTo(1);
    assertThat(requestsTo("GET", TENANT_ENDPOINT + "/")).isZero();
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install-async.json",
    "/wiremock/folio-module1/tenant-job-failed.json"
  })
  void install_negative_asyncJobFails() throws Exception {
    final var eventsBefore = eventCount(entitlementEvent(ENTITLE, FOLIO_MODULE1_ID));

    var stage = entitleExpectingFailure();

    assertThat(stage.getErrorMessage())
      .contains(FOLIO_MODULE1_ID)
      .contains("relation foo does not exist")
      .contains("schema upgrade failed");
    assertModuleEntitlements(FOLIO_MODULE1_ID, emptyEntitlements());
    assertThat(requestsTo("POST", TENANT_ENDPOINT)).isEqualTo(1);
    assertEventCountStays(entitlementEvent(ENTITLE, FOLIO_MODULE1_ID), eventsBefore);
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install-async.json",
    "/wiremock/folio-module1/tenant-job-unavailable.json",
    "/wiremock/folio-module1/tenant-job-recovered.json"
  })
  void install_positive_transientJobReadErrorIsRetried() throws Exception {
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
    entitleApplications(entitlementRequest(FOLIO_APP1_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_ID)));

    assertModuleEntitlements(FOLIO_MODULE1_ID, entitlements(entitlement(TENANT_ID, FOLIO_APP1_ID)));
    assertEntitlementEvents(entitlementEvent(ENTITLE, FOLIO_MODULE1_ID));
    assertThat(requestsTo("POST", TENANT_ENDPOINT)).isEqualTo(1);
    assertThat(requestsTo("GET", JOB_PATH)).isEqualTo(2);
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install-async.json",
    "/wiremock/folio-module1/tenant-job-not-found.json"
  })
  void install_negative_jobNotFoundFailsWithoutRepolling() throws Exception {
    final var eventsBefore = eventCount(entitlementEvent(ENTITLE, FOLIO_MODULE1_ID));

    var stage = entitleExpectingFailure();

    assertThat(stage.getErrorMessage()).contains(FOLIO_MODULE1_ID).contains("Failed to poll tenant job");
    assertModuleEntitlements(FOLIO_MODULE1_ID, emptyEntitlements());
    assertThat(requestsTo("POST", TENANT_ENDPOINT)).isEqualTo(1);
    assertThat(requestsTo("GET", JOB_PATH)).isEqualTo(1);
    assertEventCountStays(entitlementEvent(ENTITLE, FOLIO_MODULE1_ID), eventsBefore);
  }

  @Test
  @Sql("classpath:/sql/folio-entitlement.sql")
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app2/get-discovery.json",
    "/wiremock/folio-module2/uninstall-async.json",
    "/wiremock/folio-module2/tenant-job-complete.json"
  })
  void uninstall_positive_asyncPurgeCompletes() throws Exception {
    var queryParams = Map.of("purge", "true", "ignoreErrors", "true");

    var expected = extendedEntitlements(entitlement(FOLIO_APP2_ID));
    revokeEntitlements(entitlementRequest(FOLIO_APP2_ID), queryParams, expected);

    assertModuleEntitlements(FOLIO_MODULE2_ID, emptyEntitlements());
    assertEntitlementEvents(entitlementEvent(REVOKE, FOLIO_MODULE2_ID));
    assertThat(requestsTo("POST", MODULE2_TENANT_ENDPOINT)).isEqualTo(1);
    assertThat(requestsTo("GET", MODULE2_JOB_PATH)).isEqualTo(1);
    assertEgressUseGatewayHeaderOnlyOnTenantApiCalls(2);
  }

  @Test
  @Sql("classpath:/sql/folio-entitlement.sql")
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app2/get-discovery.json",
    "/wiremock/folio-module2/uninstall-async.json",
    "/wiremock/folio-module2/tenant-job-failed.json"
  })
  void uninstall_negative_asyncPurgeFails() throws Exception {
    final var eventsBefore = eventCount(entitlementEvent(REVOKE, FOLIO_MODULE2_ID));
    var request = delete(updatePathWithPrefix("/entitlements"))
      .queryParam("purge", "true")
      .queryParam("ignoreErrors", "false")
      .contentType(APPLICATION_JSON)
      .header(TOKEN, getSystemAccessToken())
      .content(asJsonString(entitlementRequest(FOLIO_APP2_ID)));

    var stage = performExpectingFailure(request, UNINSTALLER_STAGE);

    assertThat(stage.getErrorMessage())
      .contains(FOLIO_MODULE2_ID)
      .contains("drop schema failed")
      .contains("purge aborted");
    assertModuleEntitlements(FOLIO_MODULE2_ID, entitlements(entitlement(TENANT_ID, FOLIO_APP2_ID)));
    assertThat(requestsTo("POST", MODULE2_TENANT_ENDPOINT)).isEqualTo(1);
    assertEventCountStays(entitlementEvent(REVOKE, FOLIO_MODULE2_ID), eventsBefore);
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install-async.json",
    "/wiremock/folio-module1/tenant-job-never-completes.json"
  })
  void install_negative_asyncJobNeverCompletes() throws Exception {
    final var eventsBefore = eventCount(entitlementEvent(ENTITLE, FOLIO_MODULE1_ID));
    var startedAt = System.nanoTime();

    var stage = entitleExpectingFailure();

    assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isGreaterThanOrEqualTo(Duration.ofSeconds(2));
    assertThat(stage.getErrorMessage()).contains(FOLIO_MODULE1_ID).contains("did not complete within");
    assertModuleEntitlements(FOLIO_MODULE1_ID, emptyEntitlements());
    assertThat(requestsTo("POST", TENANT_ENDPOINT)).isEqualTo(1);
    assertEventCountStays(entitlementEvent(ENTITLE, FOLIO_MODULE1_ID), eventsBefore);
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app6/v1-get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app6/v1-get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json",
    "/wiremock/folio-module1/uninstall.json",
    "/wiremock/folio-module2/install-async.json",
    "/wiremock/folio-module2/tenant-job-install-failed.json",
    "/wiremock/folio-module3/install-async.json",
    "/wiremock/folio-module3/tenant-job-pending.json",
    "/wiremock/folio-module3/tenant-job-pending-2.json",
    "/wiremock/folio-module3/tenant-job-complete.json",
    "/wiremock/folio-module3/uninstall.json"
  })
  void install_negative_oneJobFailsWhileAnotherKeepsWaitingAndCompletedModulesAreRolledBack() throws Exception {
    var request = post(updatePathWithPrefix("/entitlements"))
      .queryParam("tenantParameters", "loadReference=true")
      .queryParam("ignoreErrors", "false")
      .queryParam("purgeOnRollback", "true")
      .contentType(APPLICATION_JSON)
      .header(TOKEN, getSystemAccessToken())
      .content(asJsonString(entitlementRequest(FOLIO_APP6_V1_ID)));

    var stage = performExpectingFailure(request, "folio-module2-2.0.0-folioModuleInstaller");

    assertThat(stage.getErrorMessage())
      .contains(FOLIO_MODULE2_ID)
      .contains("schema upgrade failed")
      .contains("module2 init aborted");
    assertThat(requestsTo("POST", MODULE2_TENANT_ENDPOINT)).isEqualTo(1);
    assertThat(requestsTo("GET", MODULE3_JOB_PATH)).isEqualTo(3);
    assertThat(requestsTo("POST", MODULE3_TENANT_ENDPOINT)).isEqualTo(2);
    assertThat(requestsTo("POST", TENANT_ENDPOINT)).isEqualTo(2);
    assertModuleEntitlements(FOLIO_MODULE1_ID, emptyEntitlements());
    assertModuleEntitlements(FOLIO_MODULE2_ID, emptyEntitlements());
    assertModuleEntitlements(FOLIO_MODULE3_ID, emptyEntitlements());
  }

  @Test
  @Sql("classpath:/sql/folio-entitlement.sql")
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app2-legacy/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app2/get-discovery.json",
    "/wiremock/folio-module2/disable-legacy-async.json",
    "/wiremock/folio-module2/tenant-job-complete.json"
  })
  void uninstall_positive_legacyDisableJobCompletes() throws Exception {
    var queryParams = Map.of("purge", "false", "ignoreErrors", "true");

    var expected = extendedEntitlements(entitlement(FOLIO_APP2_ID));
    revokeEntitlements(entitlementRequest(FOLIO_APP2_ID), queryParams, expected);

    assertModuleEntitlements(FOLIO_MODULE2_ID, emptyEntitlements());
    assertEntitlementEvents(entitlementEvent(REVOKE, FOLIO_MODULE2_ID));
    assertThat(requestsTo("POST", MODULE2_TENANT_ENDPOINT + "/disable")).isEqualTo(1);
    assertThat(requestsTo("GET", MODULE2_JOB_PATH)).isEqualTo(1);
    assertEgressUseGatewayHeaderOnlyOnTenantApiCalls(2);
  }

  @Test
  @Sql("classpath:/sql/folio-entitlement.sql")
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app2-legacy/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app2/get-discovery.json",
    "/wiremock/folio-module2/disable-legacy-async.json",
    "/wiremock/folio-module2/tenant-job-failed.json"
  })
  void uninstall_negative_legacyDisableJobFails() throws Exception {
    final var eventsBefore = eventCount(entitlementEvent(REVOKE, FOLIO_MODULE2_ID));
    var request = delete(updatePathWithPrefix("/entitlements"))
      .queryParam("purge", "false")
      .queryParam("ignoreErrors", "false")
      .contentType(APPLICATION_JSON)
      .header(TOKEN, getSystemAccessToken())
      .content(asJsonString(entitlementRequest(FOLIO_APP2_ID)));

    var stage = performExpectingFailure(request, UNINSTALLER_STAGE);

    assertThat(stage.getErrorMessage()).contains(FOLIO_MODULE2_ID).contains("drop schema failed");
    assertModuleEntitlements(FOLIO_MODULE2_ID, entitlements(entitlement(TENANT_ID, FOLIO_APP2_ID)));
    assertThat(requestsTo("POST", MODULE2_TENANT_ENDPOINT + "/disable")).isEqualTo(1);
    assertEventCountStays(entitlementEvent(REVOKE, FOLIO_MODULE2_ID), eventsBefore);
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app6/v1-get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app6/v1-get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json",
    "/wiremock/folio-module2/install.json",
    "/wiremock/folio-module3/install.json",

    "/wiremock/mgr-applications/folio-app6/v2-get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app6/v2-get-discovery.json",
    "/wiremock/folio-module2-1/install-async.json",
    "/wiremock/folio-module2-1/tenant-job-pending.json",
    "/wiremock/folio-module2-1/tenant-job-pending-2.json",
    "/wiremock/folio-module2-1/tenant-job-complete.json",
    "/wiremock/folio-module4/install.json"
  })
  void upgrade_positive_asyncJobCompletes() throws Exception {
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
    entitleApplications(entitlementRequest(FOLIO_APP6_V1_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP6_V1_ID)));

    upgradeApplications(entitlementRequest(FOLIO_APP6_V2_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP6_V2_ID)));

    var upgradedModules = List.of(FOLIO_MODULE1_ID, FOLIO_MODULE2_V2_ID, FOLIO_MODULE4_ID);
    var expected = entitlements(entitlementWithModules(TENANT_ID, FOLIO_APP6_V2_ID, upgradedModules));
    assertEntitlementsWithModules(queryByTenantAndAppId(FOLIO_APP6_V2_ID), expected);
    assertThat(requestsTo("POST", MODULE21_TENANT_ENDPOINT)).isEqualTo(1);
    assertThat(requestsTo("GET", MODULE21_JOB_PATH)).isEqualTo(3);
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app6/v1-get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app6/v1-get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json",
    "/wiremock/folio-module2/install.json",
    "/wiremock/folio-module3/install.json",

    "/wiremock/mgr-applications/folio-app6/v2-get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app6/v2-get-discovery.json",
    "/wiremock/folio-module2-1/install-async.json",
    "/wiremock/folio-module2-1/tenant-job-failed.json",
    "/wiremock/folio-module4/install.json"
  })
  void upgrade_negative_asyncJobFails() throws Exception {
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
    entitleApplications(entitlementRequest(FOLIO_APP6_V1_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP6_V1_ID)));
    var request = put(updatePathWithPrefix("/entitlements"))
      .queryParam("tenantParameters", "loadReference=true")
      .queryParam("ignoreErrors", "true")
      .contentType(APPLICATION_JSON)
      .header(TOKEN, getSystemAccessToken())
      .content(asJsonString(entitlementRequest(FOLIO_APP6_V2_ID)));

    var stage = performExpectingFailure(request, "folio-module2-2.1.0-folioModuleUpdater");

    assertThat(stage.getErrorMessage())
      .contains(FOLIO_MODULE2_V2_ID)
      .contains("upgrade script failed")
      .contains("migration 2.1.0 aborted");
    assertModuleEntitlements(FOLIO_MODULE2_V2_ID, emptyEntitlements());
    assertThat(requestsTo("POST", MODULE21_TENANT_ENDPOINT)).isEqualTo(1);
  }

  private FlowStage entitleExpectingFailure() throws Exception {
    var request = post(updatePathWithPrefix("/entitlements"))
      .contentType(APPLICATION_JSON)
      .header(TOKEN, getSystemAccessToken())
      .content(asJsonString(entitlementRequest(FOLIO_APP1_ID)));
    Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true").forEach(request::queryParam);

    return performExpectingFailure(request, INSTALLER_STAGE);
  }

  private FlowStage performExpectingFailure(MockHttpServletRequestBuilder request, String stageName)
    throws Exception {
    var response = mockMvc.perform(request).andExpect(content().contentType(APPLICATION_JSON))
      .andReturn().getResponse();

    return getFlowStage(extractFlowId(response), stageName, mockMvc);
  }

  private static String extractFlowId(MockHttpServletResponse response) throws Exception {
    var matcher = FAILED_OR_CANCELLED_FLOW.matcher(response.getContentAsString());
    assertThat(matcher.matches()).isTrue();
    return matcher.group(1);
  }

  private long eventCount(EntitlementEvent event) {
    return getEvents(entitlementTopic(), EntitlementEvent.class).stream()
      .filter(consumerRecord -> event.equals(consumerRecord.value()))
      .count();
  }

  private void assertEventCountStays(EntitlementEvent event, long expectedCount) {
    await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5))
      .untilAsserted(() -> assertThat(eventCount(event)).isEqualTo(expectedCount));
  }

  /**
   * Checks the recorded traffic of the stubbed modules and other services: every tenant API call and tenant job poll
   * carries {@code x-okapi-egress-use-gateway: true}, every other request does not carry the header.
   */
  private void assertEgressUseGatewayHeaderOnlyOnTenantApiCalls(int expectedTenantApiRequests) throws Exception {
    var requests = getWireMockClient().getServeEvents().stream().map(ServeEvent::getRequest).toList();
    var tenantApiRequests = requests.stream().filter(request -> request.getUrl().contains("/_/tenant")).toList();
    var otherRequests = requests.stream().filter(request -> !request.getUrl().contains("/_/tenant")).toList();

    assertThat(tenantApiRequests).hasSize(expectedTenantApiRequests)
      .allSatisfy(request -> assertThat(request.getHeader(EGRESS_USE_GATEWAY_HEADER))
        .as("%s %s", request.getMethod(), request.getUrl()).isEqualTo(EGRESS_USE_GATEWAY_VALUE));
    assertThat(otherRequests).isNotEmpty()
      .allSatisfy(request -> assertThat(request.containsHeader(EGRESS_USE_GATEWAY_HEADER))
        .as("%s %s", request.getMethod(), request.getUrl()).isFalse());
  }

  private long requestsTo(String method, String urlPrefix) throws Exception {
    var wireMockClient = new WireMock(new URI(wmAdminClient.getWireMockUrl()).getHost(),
      wmAdminClient.getWireMockPort());
    return wireMockClient.getServeEvents().stream()
      .filter(event -> event.getRequest().getMethod().getName().equals(method))
      .filter(event -> event.getRequest().getUrl().startsWith(urlPrefix))
      .count();
  }
}
