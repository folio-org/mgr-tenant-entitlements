package org.folio.entitlement.it;

import org.folio.entitlement.support.KeycloakTestClientConfiguration.KeycloakTestClient;
import org.folio.jwt.openid.OpenidJwtParserProvider;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.keycloak.admin.client.Keycloak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = {
  "application.security.enabled=true",
  "application.keycloak.import.enabled=true",
  "application.kong.module-self-url=http://mgr-tenant-entitlements:8000"
})
class FolioEntitlementWithSecurityIT extends FolioEntitlementIT {

  @Autowired private KeycloakTestClient keycloakTestClient;

  @BeforeAll
  static void beforeAll(@Autowired Keycloak keycloak) {
    var accessTokenResponse = keycloak.tokenManager().grantToken();
    var accessTokenString = accessTokenResponse.getToken();
    System.setProperty(SYSTEM_ACCESS_TOKEN_SYSTEM_PROPERTY_KEY, accessTokenString);
  }

  /**
   * Invalidates cache before each test because keycloak realm is always recreates, so it prevents old keys to work for
   * newly issued tokens.
   */
  @BeforeEach
  void setUp(@Autowired OpenidJwtParserProvider openidJwtParserProvider) {
    openidJwtParserProvider.invalidateCache();
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
