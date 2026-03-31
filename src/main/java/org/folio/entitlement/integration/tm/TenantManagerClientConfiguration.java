package org.folio.entitlement.integration.tm;

import static org.folio.common.utils.tls.HttpClientTlsUtils.buildHttpServiceClient;

import org.folio.entitlement.integration.interceptor.TokenRefreshRequestInterceptor;
import org.folio.entitlement.integration.token.AdminTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class TenantManagerClientConfiguration {

  @Bean
  public TenantManagerClient tenantManagerClient(TenantManagerProperties properties,
    AdminTokenProvider adminTokenProvider) {
    var interceptor = new TokenRefreshRequestInterceptor(adminTokenProvider);
    return buildHttpServiceClient(
      RestClient.builder().baseUrl(properties.getUrl()).requestInterceptor(interceptor),
      properties.getTls(), properties.getUrl(), TenantManagerClient.class);
  }
}
