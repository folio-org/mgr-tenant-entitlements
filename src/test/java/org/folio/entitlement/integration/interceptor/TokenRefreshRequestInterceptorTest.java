package org.folio.entitlement.integration.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.common.utils.OkapiHeaders.TOKEN;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import feign.RequestTemplate;
import java.util.Collection;
import org.folio.entitlement.integration.keycloak.KeycloakAdminTokenProvider;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TokenRefreshRequestInterceptorTest {

  private static final String FRESH_TOKEN = "fresh-token";

  @Mock private KeycloakAdminTokenProvider keycloakAdminTokenProvider;

  private TokenRefreshRequestInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new TokenRefreshRequestInterceptor(keycloakAdminTokenProvider);
  }

  @Test
  void apply_positive_replacesToken() {
    when(keycloakAdminTokenProvider.getToken(OKAPI_TOKEN)).thenReturn(FRESH_TOKEN);

    var template = new RequestTemplate();
    template.header(TOKEN, OKAPI_TOKEN);

    interceptor.apply(template);

    Collection<String> tokenHeaders = template.headers().get(TOKEN);
    assertThat(tokenHeaders).containsExactly(FRESH_TOKEN);
    verify(keycloakAdminTokenProvider).getToken(OKAPI_TOKEN);
  }

  @Test
  void apply_positive_noTokenHeader() {
    var template = new RequestTemplate();

    interceptor.apply(template);

    Collection<String> tokenHeaders = template.headers().get(TOKEN);
    assertThat(tokenHeaders).isNull();
    verifyNoInteractions(keycloakAdminTokenProvider);
  }

  @Test
  void apply_positive_emptyTokenHeaders() {
    var template = new RequestTemplate();
    template.header(TOKEN, new String[0]);

    interceptor.apply(template);

    verifyNoInteractions(keycloakAdminTokenProvider);
  }

  @Test
  void apply_positive_multipleTokenHeaders() {
    when(keycloakAdminTokenProvider.getToken(OKAPI_TOKEN)).thenReturn(FRESH_TOKEN);

    var template = new RequestTemplate();
    template.header(TOKEN, OKAPI_TOKEN, "another-token");

    interceptor.apply(template);

    // Should only use the first token
    Collection<String> tokenHeaders = template.headers().get(TOKEN);
    assertThat(tokenHeaders).containsExactly(FRESH_TOKEN);
    verify(keycloakAdminTokenProvider).getToken(OKAPI_TOKEN);
  }
}
