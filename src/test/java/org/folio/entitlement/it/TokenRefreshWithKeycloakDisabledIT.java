package org.folio.entitlement.it;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;

import feign.RequestTemplate;
import org.folio.entitlement.integration.interceptor.TokenRefreshRequestInterceptor;
import org.folio.entitlement.integration.token.TokenProvider;
import org.folio.entitlement.service.EntitlementApplicationService;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@TestPropertySource(properties = {
  "application.keycloak.enabled=false",
  "application.keycloak.import.enabled=false"
})
@Sql(executionPhase = BEFORE_TEST_METHOD, scripts = "classpath:/sql/folio-entitlement.sql")
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
class TokenRefreshWithKeycloakDisabledIT extends BaseIntegrationTest {

  private static final String TEST_TENANT = "test";
  private static final String USER_TOKEN = "user-token-12345";

  @Autowired private EntitlementApplicationService entitlementApplicationService;

  @SpyBean private TokenRefreshRequestInterceptor tokenRefreshRequestInterceptor;
  @SpyBean private TokenProvider tokenProvider;

  @MockitoBean private Keycloak keycloak;

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get-query-by-name.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json"
  })
  void shouldPassThroughUserTokenWhenKeycloakDisabled() {
    // When: Call service with Keycloak disabled
    entitlementApplicationService.getApplicationDescriptorsByTenantName(TEST_TENANT, USER_TOKEN, 0, 10);

    // Then: Interceptor is still called (it's always registered)
    verify(tokenRefreshRequestInterceptor, atLeast(1)).apply(any(RequestTemplate.class));

    // TokenProvider should return the user token as-is
    verify(tokenProvider, atLeast(1)).getToken(eq(USER_TOKEN));

    // Keycloak should never be called
    verify(keycloak, never()).tokenManager();
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get-query-by-name.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json"
  })
  void shouldHandleMultipleCallsWithKeycloakDisabled() {
    // When: Make multiple calls
    entitlementApplicationService.getApplicationDescriptorsByTenantName(TEST_TENANT, USER_TOKEN, 0, 10);
    entitlementApplicationService.getApplicationDescriptorsByTenantName(TEST_TENANT, USER_TOKEN, 0, 10);

    // Then: TokenProvider should be called for each request
    verify(tokenProvider, atLeast(4)).getToken(eq(USER_TOKEN));

    // Keycloak should never be called
    verify(keycloak, never()).tokenManager();
  }
}
