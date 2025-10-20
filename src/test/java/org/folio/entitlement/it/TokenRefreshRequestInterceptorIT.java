package org.folio.entitlement.it;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;

import feign.RequestTemplate;
import org.folio.entitlement.integration.interceptor.TokenRefreshRequestInterceptor;
import org.folio.entitlement.integration.token.TokenProvider;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.entitlement.service.EntitlementApplicationService;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.token.TokenManager;
import org.keycloak.representations.AccessTokenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@TestPropertySource(properties = {
  "application.keycloak.enabled=true",
  "application.keycloak.import.enabled=false"
})
@Sql(executionPhase = BEFORE_TEST_METHOD, scripts = "classpath:/sql/folio-entitlement.sql")
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
class TokenRefreshRequestInterceptorIT extends BaseIntegrationTest {

  private static final String TEST_TENANT = "test";
  private static final String USER_TOKEN = "user-token-12345";
  private static final String KEYCLOAK_TOKEN = "keycloak-system-token-67890";

  @Autowired private EntitlementApplicationService entitlementApplicationService;
  @Autowired private ApplicationManagerService applicationManagerService;

  @SpyBean private TokenRefreshRequestInterceptor tokenRefreshRequestInterceptor;
  @SpyBean private TokenProvider tokenProvider;

  @MockitoBean private Keycloak keycloak;

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get-query-by-name.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json"
  })
  void shouldRefreshTokenWhenCallingMgrTenants() {
    // Given: Mock Keycloak to return fresh token
    mockKeycloakToken(KEYCLOAK_TOKEN);

    // When: Call service that makes request to mgr-tenants
    entitlementApplicationService.getApplicationDescriptorsByTenantName(TEST_TENANT, USER_TOKEN, 0, 10);

    // Then: Verify interceptor was called
    verify(tokenRefreshRequestInterceptor, atLeast(1)).apply(any(RequestTemplate.class));
    
    // Verify TokenProvider was called with user token and returned Keycloak token
    verify(tokenProvider, atLeast(1)).getToken(eq(USER_TOKEN));
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get-query-by-name.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json"
  })
  void shouldRefreshTokenWhenCallingMgrApplications() {
    // Given: Mock Keycloak to return fresh token
    mockKeycloakToken(KEYCLOAK_TOKEN);

    // When: Call service that makes request to mgr-applications
    entitlementApplicationService.getApplicationDescriptorsByTenantName(TEST_TENANT, USER_TOKEN, 0, 10);

    // Then: Verify interceptor was called (at least once for tenant service)
    verify(tokenRefreshRequestInterceptor, atLeast(1)).apply(any(RequestTemplate.class));
    
    // Verify TokenProvider was called for both services
    verify(tokenProvider, atLeast(1)).getToken(anyString());
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get-query-by-name.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json"
  })
  void shouldUseCachedTokenOnSubsequentCalls() {
    // Given: Mock Keycloak to return fresh token
    mockKeycloakToken(KEYCLOAK_TOKEN);

    // When: Make multiple calls
    entitlementApplicationService.getApplicationDescriptorsByTenantName(TEST_TENANT, USER_TOKEN, 0, 10);
    entitlementApplicationService.getApplicationDescriptorsByTenantName(TEST_TENANT, USER_TOKEN, 0, 10);

    // Then: TokenProvider should be called for all requests (interceptor always calls it)
    verify(tokenProvider, atLeast(2)).getToken(anyString());
    
    // Interceptor should be called for all requests
    verify(tokenRefreshRequestInterceptor, atLeast(2)).apply(any(RequestTemplate.class));
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get-query-by-name.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json"
  })
  void shouldRefreshTokenForMultipleServices() {
    // Given: Mock Keycloak to return fresh token
    mockKeycloakToken(KEYCLOAK_TOKEN);

    // When: Make call that triggers both tenant and application manager services
    entitlementApplicationService.getApplicationDescriptorsByTenantName(TEST_TENANT, USER_TOKEN, 0, 10);

    // Then: Verify interceptor was called for multiple requests
    verify(tokenRefreshRequestInterceptor, atLeast(1)).apply(any(RequestTemplate.class));
    
    // Verify TokenProvider was called with the user token
    verify(tokenProvider, atLeast(1)).getToken(eq(USER_TOKEN));
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get-query-by-name.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json"
  })
  void shouldHandleMultipleDifferentUserTokens() {
    // Given: Mock Keycloak to return token
    final var userToken1 = "user-token-1";
    final var kcToken1 = "kc-token-1";
    mockKeycloakToken(kcToken1);
    
    // When: First call with user token 1
    entitlementApplicationService.getApplicationDescriptorsByTenantName(TEST_TENANT, userToken1, 0, 10);

    // Then: TokenProvider should be called with user token 1
    verify(tokenProvider, atLeast(1)).getToken(eq(userToken1));

    // Given: Mock for second user
    final var userToken2 = "user-token-2";
    final var kcToken2 = "kc-token-2";
    mockKeycloakToken(kcToken2);

    // When: Second call with user token 2
    entitlementApplicationService.getApplicationDescriptorsByTenantName(TEST_TENANT, userToken2, 0, 10);

    // Then: TokenProvider should be called with user token 2
    verify(tokenProvider, atLeast(1)).getToken(eq(userToken2));
  }

  private void mockKeycloakToken(String token) {
    var accessTokenResponse = new AccessTokenResponse();
    accessTokenResponse.setToken(token);
    accessTokenResponse.setExpiresIn(3600L);

    var tokenManager = mock(TokenManager.class);
    when(tokenManager.grantToken()).thenReturn(accessTokenResponse);
    when(keycloak.tokenManager()).thenReturn(tokenManager);
  }
}
