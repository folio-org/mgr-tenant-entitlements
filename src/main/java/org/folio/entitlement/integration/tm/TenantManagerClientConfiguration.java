package org.folio.entitlement.integration.tm;

import static org.folio.common.utils.tls.FeignClientTlsUtils.getOkHttpClient;

import feign.RequestInterceptor;
import feign.okhttp.OkHttpClient;
import org.folio.entitlement.integration.interceptor.TokenRefreshRequestInterceptor;
import org.folio.entitlement.integration.token.AdminTokenProvider;
import org.springframework.context.annotation.Bean;

public class TenantManagerClientConfiguration {

  @Bean
  public OkHttpClient feignClient(okhttp3.OkHttpClient okHttpClient, TenantManagerProperties properties) {
    return getOkHttpClient(okHttpClient, properties.getTls());
  }

  @Bean
  public RequestInterceptor tenantManagerRequestInterceptor(AdminTokenProvider adminTokenProvider) {
    return new TokenRefreshRequestInterceptor(adminTokenProvider);
  }
}
