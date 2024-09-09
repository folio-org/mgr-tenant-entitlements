package org.folio.entitlement.it;

import org.folio.entitlement.support.KeycloakTestClientConfiguration.KeycloakTestClient;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.keycloak.admin.client.Keycloak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = {
  "application.security.enabled=true",
  "application.router.path-prefix=mte",
  "application.keycloak.import.enabled=true",
  "application.kong.module-self-url=http://mgr-tenant-entitlements:8000/mte"
})
class FolioEntitlementSecuredPathPrefixIT extends AbstractFolioEntitlementIT {

  @Autowired private KeycloakTestClient keycloakTestClient;

  @BeforeAll
  static void beforeAll(@Autowired Keycloak keycloak) {
    var accessTokenResponse = keycloak.tokenManager().grantToken();
    var accessTokenString = accessTokenResponse.getToken();
    System.setProperty(ROUTER_PATH_PREFIX_SYSTEM_PROPERTY_KEY, "mte");
    System.setProperty(SYSTEM_ACCESS_TOKEN_SYSTEM_PROPERTY_KEY, accessTokenString);
  }

  @AfterAll
  static void afterAll() {
    System.clearProperty(ROUTER_PATH_PREFIX_SYSTEM_PROPERTY_KEY);
    System.clearProperty(SYSTEM_ACCESS_TOKEN_SYSTEM_PROPERTY_KEY);
  }

  @Override
  protected String getUserAccessToken() {
    return keycloakTestClient.generateAccessToken(TEST_TENANT);
  }
}
