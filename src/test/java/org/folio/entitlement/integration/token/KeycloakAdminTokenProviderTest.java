package org.folio.entitlement.integration.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.entitlement.integration.keycloak.KeycloakAdminTokenProvider;
import org.folio.entitlement.integration.keycloak.KeycloakCacheableService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.AccessTokenResponse;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KeycloakAdminTokenProviderTest {

  private static final String KEYCLOAK_TOKEN = "kc-token";

  @Mock private KeycloakCacheableService keycloakCacheableService;

  private KeycloakAdminTokenProvider keycloakAdminTokenProvider;

  @BeforeEach
  void setUp() {
    keycloakAdminTokenProvider = new KeycloakAdminTokenProvider(keycloakCacheableService);
  }

  @Test
  void getToken_positive() {
    var accessTokenResponse = new AccessTokenResponse();
    accessTokenResponse.setToken(KEYCLOAK_TOKEN);

    when(keycloakCacheableService.getAccessToken(OKAPI_TOKEN)).thenReturn(accessTokenResponse);

    var result = keycloakAdminTokenProvider.getToken(OKAPI_TOKEN);

    assertThat(result).isEqualTo(KEYCLOAK_TOKEN);
    verify(keycloakCacheableService).getAccessToken(OKAPI_TOKEN);
  }
}
