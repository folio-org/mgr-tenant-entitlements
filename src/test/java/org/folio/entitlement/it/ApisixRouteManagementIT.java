package org.folio.entitlement.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.folio.entitlement.support.TestValues.entitlementRequest;
import static org.folio.entitlement.support.TestValues.extendedEntitlements;
import static org.folio.entitlement.support.extensions.impl.ApisixGatewayExtension.APISIX_ADMIN_KEY;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.entitlement.support.extensions.EnableApisixGateway;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.folio.tools.apisix.client.ApisixAdminClient;
import org.folio.tools.apisix.client.ApisixAdminClient.ApisixEntry;
import org.folio.tools.apisix.model.ApisixRoute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.client.HttpClientErrorException;

@IntegrationTest
@EnableApisixGateway
@TestPropertySource(properties = {
  "application.apigw.enabled=true",
  "application.apigw.type=apisix",
  "application.apigw.url=${apisix.url}",
  "application.apigw.api-key=" + APISIX_ADMIN_KEY,
  "application.keycloak.enabled=false",
  "application.apigw.tenant-checks.enabled=true"
})
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
class ApisixRouteManagementIT extends BaseIntegrationTest {

  private static final String FOLIO_APP1_ID = "folio-app1-1.0.0";
  private static final String FOLIO_APP1_V1_1_ID = "folio-app1-1.1.0";
  private static final String FOLIO_APP1_V2_ID = "folio-app1-2.0.0";
  private static final String FOLIO_MODULE1_V2_ID = "folio-module1-2.0.0";
  private static final String TENANT_VAR = "http_x_okapi_tenant";

  @Autowired private ApisixAdminClient apisixAdminClient;
  @Autowired private ApplicationContext applicationContext;

  @Test
  void gatewayTypeSelection_positive_apisixBeansActive() {
    assertThat(applicationContext.containsBean("folioApisixGatewayService")).isTrue();
    assertThat(applicationContext.containsBean("folioKongGatewayService")).isFalse();
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json",
    "/wiremock/folio-module1/uninstall.json"
  })
  void entitleAndRevoke_positive_apisixRoutesCreatedAndDeleted() throws Exception {
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");

    entitleApplications(entitlementRequest(FOLIO_APP1_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_ID)));

    assertThat(apisixAdminClient.getService(FOLIO_MODULE1_ID).getValue()).isNotNull();
    var routes = getModuleRoutes(FOLIO_MODULE1_ID);
    assertThat(routes).isNotEmpty().allSatisfy(route -> assertThat(getTenants(route)).contains("test"));

    revokeEntitlements(entitlementRequest(FOLIO_APP1_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_ID)));

    assertThat(getModuleRoutes(FOLIO_MODULE1_ID)).isEmpty();
    assertThatThrownBy(() -> apisixAdminClient.getService(FOLIO_MODULE1_ID))
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

    var routesBeforeUpgrade = getModuleRoutes(FOLIO_MODULE1_ID);
    assertThat(routesBeforeUpgrade).isNotEmpty();

    upgradeApplications(entitlementRequest(FOLIO_APP1_V1_1_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_V1_1_ID)));

    assertThat(apisixAdminClient.getService(FOLIO_MODULE1_ID).getValue()).isNotNull();
    var routesAfterUpgrade = getModuleRoutes(FOLIO_MODULE1_ID);
    assertThat(routesAfterUpgrade)
      .extracting(ApisixRoute::getId)
      .containsExactlyInAnyOrderElementsOf(routesBeforeUpgrade.stream().map(ApisixRoute::getId).toList());
    assertThat(routesAfterUpgrade).allSatisfy(route -> assertThat(getTenants(route)).contains("test"));

    revokeEntitlements(entitlementRequest(FOLIO_APP1_V1_1_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_V1_1_ID)));

    assertThatThrownBy(() -> apisixAdminClient.getService(FOLIO_MODULE1_ID))
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

    assertThat(apisixAdminClient.getService(FOLIO_MODULE1_ID).getValue()).isNotNull();
    var routesBeforeUpgrade = getModuleRoutes(FOLIO_MODULE1_ID);
    assertThat(routesBeforeUpgrade).isNotEmpty();

    upgradeApplications(entitlementRequest(FOLIO_APP1_V2_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_V2_ID)));

    assertThatThrownBy(() -> apisixAdminClient.getService(FOLIO_MODULE1_ID))
      .isInstanceOf(HttpClientErrorException.NotFound.class);
    assertThat(getModuleRoutes(FOLIO_MODULE1_ID)).isEmpty();
    assertThat(apisixAdminClient.getService(FOLIO_MODULE1_V2_ID).getValue()).isNotNull();
    var routesAfterUpgrade = getModuleRoutes(FOLIO_MODULE1_V2_ID);
    assertThat(routesAfterUpgrade)
      .isNotEmpty()
      .extracting(ApisixRoute::getId)
      .doesNotContainAnyElementsOf(routesBeforeUpgrade.stream().map(ApisixRoute::getId).toList());
    assertThat(routesAfterUpgrade).allSatisfy(route -> assertThat(getTenants(route)).contains("test"));

    revokeEntitlements(entitlementRequest(FOLIO_APP1_V2_ID), queryParams,
      extendedEntitlements(entitlement(FOLIO_APP1_V2_ID)));

    assertThatThrownBy(() -> apisixAdminClient.getService(FOLIO_MODULE1_V2_ID))
      .isInstanceOf(HttpClientErrorException.NotFound.class);
  }

  private List<ApisixRoute> getModuleRoutes(String moduleId) {
    return apisixAdminClient.getRoutes(1, 100).getList().stream()
      .map(ApisixEntry::getValue)
      .filter(Objects::nonNull)
      .filter(route -> route.getLabels() != null && moduleId.equals(route.getLabels().get("module")))
      .toList();
  }

  private static List<String> getTenants(ApisixRoute route) {
    return route.getVars().stream()
      .filter(condition -> TENANT_VAR.equals(condition.get(0)) && "in".equals(condition.get(1)))
      .findFirst()
      .map(condition -> ((List<?>) condition.get(2)).stream().map(Object::toString).toList())
      .orElse(List.of());
  }
}
