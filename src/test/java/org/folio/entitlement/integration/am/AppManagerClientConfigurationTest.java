package org.folio.entitlement.integration.am;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Contract;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import feign.codec.Encoder;
import okhttp3.OkHttpClient;
import org.folio.entitlement.integration.interceptor.TokenRefreshRequestInterceptor;
import org.folio.entitlement.integration.keycloak.KeycloakAdminTokenProvider;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class AppManagerClientConfigurationTest {

  private final AppManagerClientConfiguration appManagerClientConfiguration = new AppManagerClientConfiguration();
  @Mock private OkHttpClient okHttpClient;
  @Mock private Contract contract;
  @Mock private Encoder encoder;
  @Mock private Decoder decoder;
  @Mock private KeycloakAdminTokenProvider keycloakAdminTokenProvider;
  @Mock private RequestInterceptor requestInterceptor;

  @BeforeEach
  void setUp() {
    appManagerClientConfiguration.setUrl("https://test-app-manager.dev");
  }

  @Test
  void applicationManagerClient_positive() {
    var feignClient = appManagerClientConfiguration.applicationManagerClient(
      okHttpClient, contract, encoder, decoder, requestInterceptor);
    assertThat(feignClient).isNotNull();
  }

  @Test
  void applicationManagerRequestInterceptor_positive() {
    var interceptor = appManagerClientConfiguration.applicationManagerRequestInterceptor(keycloakAdminTokenProvider);
    assertThat(interceptor).isNotNull().isInstanceOf(TokenRefreshRequestInterceptor.class);
  }
}
