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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.client.HttpClientErrorException;

@IntegrationTest
@TestPropertySource(properties = {
  "application.kong.enabled=true",
  "application.keycloak.enabled=false",
})
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
class KongRouteManagementIT extends BaseIntegrationTest {

  private static final String FOLIO_APP1_ID = "folio-app1-1.0.0";

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
}
