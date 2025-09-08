package org.folio.entitlement.it;

import static java.time.Duration.ofMillis;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.folio.entitlement.configuration.cache.CacheConfiguration.ACCESS_TOKEN;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.folio.entitlement.integration.tm.TenantManagerService;
import org.folio.entitlement.integration.tm.model.Tenant;
import org.folio.entitlement.repository.EntitlementRepository;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.entitlement.service.EntitlementApplicationService;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.security.integration.keycloak.service.KeycloakStoreKeyProvider;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.token.TokenManager;
import org.keycloak.representations.AccessTokenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@IntegrationTest
@TestPropertySource(properties = {
  "application.keycloak.enabled=true",
  "application.keycloak.import.enabled=false"
})
class EntitlementApplicationServiceTokenCacheIT extends BaseIntegrationTest {

  @Autowired
  private EntitlementApplicationService entitlementApplicationService;

  @MockitoBean
  private Keycloak keycloak;
  @MockitoBean
  private TenantManagerService tenantManagerService;
  @MockitoBean
  private EntitlementRepository entitlementRepository;
  @MockitoBean
  private ApplicationManagerService applicationManagerService;
  @MockitoBean
  private KeycloakStoreKeyProvider keycloakStoreKeyProvider;
  @Autowired
  private CacheManager cacheManager;

  @Test
  void getApplicationDescriptorsByTenantName_getsTokenFromKeycloakAndCachesIt() {
    var tenant = Tenant.of(UUID.randomUUID(), null, null);
    var userToken = UUID.randomUUID().toString();
    var accessTokenResponse = createAndMockAccessTokenResponse(60);
    doReturn(tenant)
      .when(tenantManagerService).findTenantByName(tenant.getId().toString(), accessTokenResponse.getToken());

    entitlementApplicationService.getApplicationDescriptorsByTenantName(tenant.getId().toString(), userToken, 0, 10);

    verify(keycloak, times(1)).tokenManager();
    var cached = cacheManager.getCache(ACCESS_TOKEN).get(userToken, AccessTokenResponse.class);
    assertThat(cached).isEqualTo(accessTokenResponse);
  }

  @Test
  void getApplicationDescriptorsByTenantName_getsTokenFromCache_secondTime() {
    var tenant = Tenant.of(UUID.randomUUID(), null, null);
    var userToken = UUID.randomUUID().toString();
    var accessTokenResponse = createAndMockAccessTokenResponse(60);
    doReturn(tenant)
      .when(tenantManagerService).findTenantByName(tenant.getId().toString(), accessTokenResponse.getToken());

    entitlementApplicationService.getApplicationDescriptorsByTenantName(tenant.getId().toString(), userToken, 0, 10);
    var cached = cacheManager.getCache(ACCESS_TOKEN).get(userToken, AccessTokenResponse.class);
    assertThat(cached).isEqualTo(accessTokenResponse);

    entitlementApplicationService.getApplicationDescriptorsByTenantName(tenant.getId().toString(), userToken, 0, 10);
    verify(keycloak, times(1)).tokenManager();
  }

  @Test
  void getApplicationDescriptorsByTenantName_getsTokenFromKeycloak_ifCachedExpired() {
    var tenant = Tenant.of(UUID.randomUUID(), null, null);
    var userToken = UUID.randomUUID().toString();
    var accessTokenResponse = createAndMockAccessTokenResponse(7);
    doReturn(tenant)
      .when(tenantManagerService).findTenantByName(tenant.getId().toString(), accessTokenResponse.getToken());

    entitlementApplicationService.getApplicationDescriptorsByTenantName(tenant.getId().toString(), userToken, 0, 10);
    verify(keycloak, times(1)).tokenManager();
    var cached = cacheManager.getCache(ACCESS_TOKEN).get(userToken, AccessTokenResponse.class);
    assertThat(cached).isEqualTo(accessTokenResponse);

    await()
      .atMost(ofMillis(5000))
      .pollDelay(ofMillis(100))
      .pollInterval(ofMillis(200))
      .untilAsserted(() -> Assertions.assertThat(cacheManager.getCache(ACCESS_TOKEN).get(userToken)).isNull());

    entitlementApplicationService.getApplicationDescriptorsByTenantName(tenant.getId().toString(), userToken, 0, 10);
    verify(keycloak, times(2)).tokenManager();
    cached = cacheManager.getCache(ACCESS_TOKEN).get(userToken, AccessTokenResponse.class);
    assertThat(cached).isEqualTo(accessTokenResponse);
  }

  private AccessTokenResponse createAndMockAccessTokenResponse(long expiresInSeconds) {
    var authToken = UUID.randomUUID().toString();
    var accessTokenResponse = new AccessTokenResponse();
    accessTokenResponse.setToken(authToken);
    accessTokenResponse.setExpiresIn(expiresInSeconds);
    var tokenManager = mock(TokenManager.class);
    doReturn(accessTokenResponse).when(tokenManager).grantToken();
    doReturn(tokenManager).when(keycloak).tokenManager();
    return accessTokenResponse;
  }
}
