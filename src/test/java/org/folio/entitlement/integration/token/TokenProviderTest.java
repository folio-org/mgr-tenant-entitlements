package org.folio.entitlement.integration.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.folio.entitlement.integration.keycloak.KeycloakCacheableService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.AccessTokenResponse;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TokenProviderTest {

  private static final String KEYCLOAK_TOKEN = "kc-token";

  @Mock private KeycloakCacheableService keycloakCacheableService;

  private TokenProvider tokenProvider;

  @BeforeEach
  void setUp() {
    tokenProvider = new TokenProvider();
    tokenProvider.setKeycloakCacheableService(keycloakCacheableService);
  }

  @Test
  void getToken_positive_keycloakEnabled() {
    ReflectionTestUtils.setField(tokenProvider, "keycloakEnabled", true);
    var accessTokenResponse = new AccessTokenResponse();
    accessTokenResponse.setToken(KEYCLOAK_TOKEN);

    when(keycloakCacheableService.getAccessToken(OKAPI_TOKEN)).thenReturn(accessTokenResponse);

    var result = tokenProvider.getToken(OKAPI_TOKEN);

    assertThat(result).isEqualTo(KEYCLOAK_TOKEN);
    verify(keycloakCacheableService).getAccessToken(OKAPI_TOKEN);
  }

  @Test
  void getToken_positive_keycloakDisabled() {
    ReflectionTestUtils.setField(tokenProvider, "keycloakEnabled", false);

    var result = tokenProvider.getToken(OKAPI_TOKEN);

    assertThat(result).isEqualTo(OKAPI_TOKEN);
    verifyNoInteractions(keycloakCacheableService);
  }

  @Test
  void getToken_positive_keycloakEnabledButServiceNull() {
    ReflectionTestUtils.setField(tokenProvider, "keycloakEnabled", true);
    tokenProvider.setKeycloakCacheableService(null);

    var result = tokenProvider.getToken(OKAPI_TOKEN);

    assertThat(result).isEqualTo(OKAPI_TOKEN);
  }

  @Test
  void getToken_positive_withNullToken() {
    ReflectionTestUtils.setField(tokenProvider, "keycloakEnabled", false);

    var result = tokenProvider.getToken(null);

    assertThat(result).isNull();
    verifyNoInteractions(keycloakCacheableService);
  }

  @Test
  void getToken_positive_withEmptyToken() {
    ReflectionTestUtils.setField(tokenProvider, "keycloakEnabled", false);

    var result = tokenProvider.getToken("");

    assertThat(result).isEmpty();
    verifyNoInteractions(keycloakCacheableService);
  }
}
