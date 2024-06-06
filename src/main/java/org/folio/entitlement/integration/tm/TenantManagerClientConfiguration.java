package org.folio.entitlement.integration.tm;

import static org.folio.common.utils.FeignClientTlsUtils.getOkHttpClient;

import feign.okhttp.OkHttpClient;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;

@Log4j2
public class TenantManagerClientConfiguration {

  @Bean
  public OkHttpClient feignClient(okhttp3.OkHttpClient okHttpClient, TenantManagerProperties properties) {
    var tls = properties.getTls();
    log.warn("TenantManagerClientConfiguration.TlsProperties: {}", tls);

    return getOkHttpClient(okHttpClient, tls);
  }
}
