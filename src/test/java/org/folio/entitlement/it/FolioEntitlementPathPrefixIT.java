package org.folio.entitlement.it;

import org.folio.entitlement.support.KeycloakTestClientConfiguration;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.keycloak.admin.client.Keycloak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = {
  "application.router.path-prefix=mgr-tenant-entitlements",
  "application.kong.module-self-url=http://mgr-tenant-entitlements:8000/mgr-tenant-entitlements"
})
@Import(KeycloakTestClientConfiguration.class)
class FolioEntitlementPathPrefixIT extends AbstractFolioEntitlementIT {

  @BeforeAll
  static void beforeAll(@Autowired Keycloak keycloak) {
    var tokenManager = keycloak.tokenManager();
    tokenManager.grantToken();
    var accessTokenString = tokenManager.getAccessTokenString();
    System.setProperty(ROUTER_PATH_PREFIX_SYSTEM_PROPERTY_KEY, "mgr-tenant-entitlements");
    System.setProperty(SYSTEM_ACCESS_TOKEN_SYSTEM_PROPERTY_KEY, accessTokenString);
  }

  @AfterAll
  static void afterAll() {
    System.clearProperty(ROUTER_PATH_PREFIX_SYSTEM_PROPERTY_KEY);
    System.clearProperty(SYSTEM_ACCESS_TOKEN_SYSTEM_PROPERTY_KEY);
  }
}
