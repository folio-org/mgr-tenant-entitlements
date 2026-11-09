package org.folio.entitlement.configuration.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.folio.entitlement.integration.keycloak.configuration.properties.KeycloakConfigurationProperties;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.AccessTokenResponse;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class AccessTokenExpiryTest {

  private static final long TTL_OFFSET_MILLIS = 5000;
  private static final String KEY = "admin";

  @Mock private KeycloakConfigurationProperties keycloakProperties;

  @Test
  void expireAfterCreate_positive_tokenLivesLongerThanOffset() {
    when(keycloakProperties.getAuthorizationCacheTtlOffset()).thenReturn(TTL_OFFSET_MILLIS);
    var expiry = new AccessTokenExpiry(keycloakProperties);

    var ttl = expiry.expireAfterCreate(KEY, accessToken(60), 0);

    assertThat(Duration.ofNanos(ttl)).isEqualTo(Duration.ofSeconds(55));
  }

  @Test
  void expireAfterCreate_positive_tokenLivesShorterThanOffset_isNotRetainedInCache() {
    when(keycloakProperties.getAuthorizationCacheTtlOffset()).thenReturn(TTL_OFFSET_MILLIS);
    var expiry = new AccessTokenExpiry(keycloakProperties);
    var cache = Caffeine.newBuilder().expireAfter(expiry).<String, AccessTokenResponse>build();

    cache.put(KEY, accessToken(3));

    assertThat(cache.getIfPresent(KEY)).isNull();
  }

  private static AccessTokenResponse accessToken(long expiresInSeconds) {
    var response = new AccessTokenResponse();
    response.setToken("token");
    response.setExpiresIn(expiresInSeconds);
    return response;
  }
}
