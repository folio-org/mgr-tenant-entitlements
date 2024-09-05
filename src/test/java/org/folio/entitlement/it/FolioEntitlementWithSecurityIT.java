package org.folio.entitlement.it;

import org.folio.entitlement.support.KeycloakTestClientConfiguration.KeycloakTestClient;
import org.folio.test.extensions.EnableKeycloakSecurity;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.keycloak.admin.client.Keycloak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@EnableKeycloakSecurity
@TestPropertySource(properties = {
  "application.keycloak.import.enabled=true",
  "application.kong.module-self-url=http://mgr-tenant-entitlements:8000"
})
class FolioEntitlementWithSecurityIT extends FolioEntitlementIT {

  @Autowired private KeycloakTestClient keycloakTestClient;

  @BeforeAll
  static void beforeAll(@Autowired Keycloak keycloak) {
    var accessTokenString = keycloak.tokenManager().getAccessTokenString();
    System.setProperty(SYSTEM_ACCESS_TOKEN_SYSTEM_PROPERTY_KEY, accessTokenString);
  }

  @AfterAll
  static void afterAll() {
    System.clearProperty(SYSTEM_ACCESS_TOKEN_SYSTEM_PROPERTY_KEY);
  }

  @Override
  protected String getUserAccessToken() {
    return keycloakTestClient.generateAccessToken(TEST_TENANT);
  }
}
