package org.folio.entitlement.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.types.IntegrationTest;
import org.folio.tools.kong.client.KongAdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = {
  "application.security.enabled=true",
  "application.keycloak.enabled=false",
  "application.apigw.module-self-url=https://test-mgr-tenant-entitlements:443",
  "application.apigw.register-module=true",
  "application.apigw.tls.enabled=true",
  "application.apigw.tls.trust-store-path=classpath:certificates/test.truststore.jks",
  "application.apigw.tls.trust-store-password=secretpassword",
  "application.apigw.tls.trust-store-type=JKS"
})
class ApiGatewayRegistrationIT extends BaseIntegrationTest {

  @Autowired private KongAdminClient kongAdminClient;

  @Test
  void verifyModuleRegistration() {
    var moduleName = "mgr-tenant-entitlements-4.0.0";
    var service = kongAdminClient.getService(moduleName);
    assertThat(service).satisfies(s -> {
      assertThat(s.getProtocol()).isEqualTo("https");
      assertThat(s.getPort()).isEqualTo(443);
      assertThat(s.getHost()).isEqualTo("test-mgr-tenant-entitlements");
    });

    var routes = kongAdminClient.getRoutesByTag(moduleName, null);
    assertThat(routes.getData()).hasSize(19);
  }
}
