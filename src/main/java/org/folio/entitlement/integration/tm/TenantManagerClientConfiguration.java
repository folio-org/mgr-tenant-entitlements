package org.folio.entitlement.integration.tm;

import static org.folio.common.utils.tls.FeignClientTlsUtils.getOkHttpClient;

import feign.okhttp.OkHttpClient;
import org.springframework.context.annotation.Bean;

public class TenantManagerClientConfiguration {

  @Bean
  public OkHttpClient feignClient(okhttp3.OkHttpClient okHttpClient, TenantManagerProperties properties) {
    return getOkHttpClient(okHttpClient, properties.getTls());
  }
}
