package org.folio.entitlement.integration.tm;

import static org.folio.common.utils.tls.FeignClientTlsUtils.getOkHttpClient;

import feign.RequestInterceptor;
import feign.okhttp.OkHttpClient;
import org.folio.entitlement.integration.token.TokenProvider;
import org.springframework.context.annotation.Bean;

public class TenantManagerClientConfiguration {

  @Bean
  public OkHttpClient feignClient(okhttp3.OkHttpClient okHttpClient, TenantManagerProperties properties) {
    return getOkHttpClient(okHttpClient, properties.getTls());
  }

  @Bean
  public RequestInterceptor tenantManagerRequestInterceptor(TokenProvider tokenProvider) {
    return new TenantManagerRequestInterceptor(tokenProvider);
  }
}
