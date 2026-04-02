package org.folio.entitlement.integration.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.common.utils.OkapiHeaders.TOKEN;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.folio.entitlement.integration.keycloak.KeycloakAdminTokenProvider;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TokenRefreshRequestInterceptorTest {

  private static final String FRESH_TOKEN = "fresh-token";

  @Mock private KeycloakAdminTokenProvider keycloakAdminTokenProvider;
  @Mock private ClientHttpRequestExecution execution;
  @Mock private HttpRequest request;
  @Mock private ClientHttpResponse response;

  private TokenRefreshRequestInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new TokenRefreshRequestInterceptor(keycloakAdminTokenProvider);
  }

  @Test
  void intercept_positive_replacesToken() throws IOException {
    when(keycloakAdminTokenProvider.getToken(OKAPI_TOKEN)).thenReturn(FRESH_TOKEN);

    var headers = new HttpHeaders();
    headers.set(TOKEN, OKAPI_TOKEN);
    when(request.getHeaders()).thenReturn(headers);
    when(execution.execute(request, new byte[0])).thenReturn(response);

    interceptor.intercept(request, new byte[0], execution);

    verify(keycloakAdminTokenProvider).getToken(OKAPI_TOKEN);
    verify(execution).execute(request, new byte[0]);
    assertThat(headers.getFirst(TOKEN)).isEqualTo(FRESH_TOKEN);
  }

  @Test
  void intercept_positive_noTokenHeader() throws IOException {
    var headers = new HttpHeaders();
    when(request.getHeaders()).thenReturn(headers);
    when(execution.execute(request, new byte[0])).thenReturn(response);

    interceptor.intercept(request, new byte[0], execution);

    verifyNoInteractions(keycloakAdminTokenProvider);
    verify(execution).execute(request, new byte[0]);
  }
}
