package org.folio.entitlement.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.folio.entitlement.support.TestValues.entitlementRequest;
import static org.folio.entitlement.support.TestValues.extendedEntitlements;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;

import java.util.Map;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.folio.tools.kong.client.KongAdminClient;
import org.folio.tools.kong.model.Route;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.client.HttpClientErrorException;

@IntegrationTest
@TestPropertySource(properties = {
  "application.apigw.enabled=true",
  "application.keycloak.enabled=false",
  "application.apigw.tenant-checks.enabled=true"
})
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
class ApiGatewayRouteManagementIT extends BaseIntegrationTest {

  private static final String FOLIO_APP1_ID = "folio-app1-1.0.0";
  private static final String FOLIO_APP1_V1_1_ID = "folio-app1-1.1.0";
  private static final String FOLIO_APP1_V2_ID = "folio-app1-2.0.0";
  private static final String FOLIO_APP1_V3_ID = "folio-app1-3.0.0";
  private static final String FOLIO_MODULE1_V2_ID = "folio-module1-2.0.0";

  @Autowired private KongAdminClient kongAdminClient;

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json",
    "/wiremock/folio-module1/uninstall.json"
  })
  void entitleAndRevoke_positive_kongRoutesCreatedAndDeleted() throws Exception {
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");

    entitleApplications(entitlementRequest(FOLIO_APP1_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_ID)));

    assertThat(kongAdminClient.getService(FOLIO_MODULE1_ID)).isNotNull();
    assertThat(kongAdminClient.getRoutesByTag(FOLIO_MODULE1_ID, null).getData()).isNotEmpty();

    revokeEntitlements(entitlementRequest(FOLIO_APP1_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_ID)));

    assertThat(kongAdminClient.getRoutesByTag(FOLIO_MODULE1_ID, null).getData()).isEmpty();
    assertThatThrownBy(() -> kongAdminClient.getService(FOLIO_MODULE1_ID))
      .isInstanceOf(HttpClientErrorException.NotFound.class);
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full-v1.1.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery-v1.1.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json",
    "/wiremock/folio-module1/uninstall.json"
  })
  void upgrade_positive_sameModuleDescriptor_serviceUpsertedButNoRouteChanges() throws Exception {
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");

    entitleApplications(entitlementRequest(FOLIO_APP1_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_ID)));

    var routesBeforeUpgrade = kongAdminClient.getRoutesByTag(FOLIO_MODULE1_ID, null).getData();
    assertThat(routesBeforeUpgrade).isNotEmpty();

    upgradeApplications(entitlementRequest(FOLIO_APP1_V1_1_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_V1_1_ID)));

    assertThat(kongAdminClient.getService(FOLIO_MODULE1_ID)).isNotNull();
    var routesAfterUpgrade = kongAdminClient.getRoutesByTag(FOLIO_MODULE1_ID, null).getData();
    assertThat(routesAfterUpgrade).hasSameSizeAs(routesBeforeUpgrade);

    revokeEntitlements(entitlementRequest(FOLIO_APP1_V1_1_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_V1_1_ID)));

    assertThatThrownBy(() -> kongAdminClient.getService(FOLIO_MODULE1_ID))
      .isInstanceOf(HttpClientErrorException.NotFound.class);
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full-v2.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery-v2.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json",
    "/wiremock/folio-module1/install-v2.json",
    "/wiremock/folio-module1/uninstall-v2.json"
  })
  void upgrade_positive_moduleVersionChanged_oldServiceDeletedAndNewRoutesCreated() throws Exception {
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");

    entitleApplications(entitlementRequest(FOLIO_APP1_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_ID)));

    assertThat(kongAdminClient.getService(FOLIO_MODULE1_ID)).isNotNull();
    var routesBeforeUpgrade = kongAdminClient.getRoutesByTag(FOLIO_MODULE1_ID, null).getData();
    assertThat(routesBeforeUpgrade).isNotEmpty();

    upgradeApplications(entitlementRequest(FOLIO_APP1_V2_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_V2_ID)));

    assertThatThrownBy(() -> kongAdminClient.getService(FOLIO_MODULE1_ID))
      .isInstanceOf(HttpClientErrorException.NotFound.class);
    assertThat(kongAdminClient.getRoutesByTag(FOLIO_MODULE1_ID, null).getData()).isEmpty();
    assertThat(kongAdminClient.getService(FOLIO_MODULE1_V2_ID)).isNotNull();
    var routesAfterUpgrade = kongAdminClient.getRoutesByTag(FOLIO_MODULE1_V2_ID, null).getData();
    assertThat(routesAfterUpgrade).isNotEmpty();
    assertThat(routesAfterUpgrade).extracting(Route::getId)
      .doesNotContainAnyElementsOf(routesBeforeUpgrade.stream().map(Route::getId).toList());

    revokeEntitlements(entitlementRequest(FOLIO_APP1_V2_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_V2_ID)));

    assertThatThrownBy(() -> kongAdminClient.getService(FOLIO_MODULE1_V2_ID))
      .isInstanceOf(HttpClientErrorException.NotFound.class);
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full-v3.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery-v3.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json",
    "/wiremock/folio-module2/install.json",
    "/wiremock/folio-module2/uninstall.json"
  })
  void upgrade_positive_deprecatedModule_serviceAndRoutesDeleted() throws Exception {
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");

    entitleApplications(entitlementRequest(FOLIO_APP1_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_ID)));

    assertThat(kongAdminClient.getService(FOLIO_MODULE1_ID)).isNotNull();
    assertThat(kongAdminClient.getRoutesByTag(FOLIO_MODULE1_ID, null).getData()).isNotEmpty();

    upgradeApplications(entitlementRequest(FOLIO_APP1_V3_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_V3_ID)));

    assertThatThrownBy(() -> kongAdminClient.getService(FOLIO_MODULE1_ID))
      .isInstanceOf(HttpClientErrorException.NotFound.class);
    assertThat(kongAdminClient.getRoutesByTag(FOLIO_MODULE1_ID, null).getData()).isEmpty();
    assertThat(kongAdminClient.getService(FOLIO_MODULE2_ID)).isNotNull();
    assertThat(kongAdminClient.getRoutesByTag(FOLIO_MODULE2_ID, null).getData()).isNotEmpty();

    revokeEntitlements(entitlementRequest(FOLIO_APP1_V3_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_V3_ID)));

    assertThatThrownBy(() -> kongAdminClient.getService(FOLIO_MODULE2_ID))
      .isInstanceOf(HttpClientErrorException.NotFound.class);
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full-v2.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery-v2.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json",
    "/wiremock/folio-module1/install-v2.json",
    "/wiremock/folio-module1/uninstall-v2.json"
  })
  void upgrade_positive_tenantChecks_tenantAddedToNewModuleRoutes() throws Exception {
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");

    entitleApplications(entitlementRequest(FOLIO_APP1_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_ID)));

    assertThat(kongAdminClient.getService(FOLIO_MODULE1_ID)).isNotNull();
    var routesAfterEntitle = kongAdminClient.getRoutesByTag(FOLIO_MODULE1_ID, null).getData();
    assertThat(routesAfterEntitle).isNotEmpty();
    assertThat(routesAfterEntitle).allSatisfy(route ->
      assertThat(route.getExpression()).contains("x_okapi_tenant == \"test\""));

    upgradeApplications(entitlementRequest(FOLIO_APP1_V2_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_V2_ID)));

    assertThatThrownBy(() -> kongAdminClient.getService(FOLIO_MODULE1_ID))
      .isInstanceOf(HttpClientErrorException.NotFound.class);
    assertThat(kongAdminClient.getService(FOLIO_MODULE1_V2_ID)).isNotNull();
    var routesAfterUpgrade = kongAdminClient.getRoutesByTag(FOLIO_MODULE1_V2_ID, null).getData();
    assertThat(routesAfterUpgrade).isNotEmpty();
    assertThat(routesAfterUpgrade).allSatisfy(route ->
      assertThat(route.getExpression()).contains("x_okapi_tenant == \"test\""));

    revokeEntitlements(entitlementRequest(FOLIO_APP1_V2_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_V2_ID)));

    assertThatThrownBy(() -> kongAdminClient.getService(FOLIO_MODULE1_V2_ID))
      .isInstanceOf(HttpClientErrorException.NotFound.class);
  }
}
