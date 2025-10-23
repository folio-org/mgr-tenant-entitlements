package org.folio.entitlement.integration.am;

import feign.Contract;
import feign.Feign;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import feign.codec.Encoder;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import org.folio.common.configuration.properties.TlsProperties;
import org.folio.common.utils.tls.FeignClientTlsUtils;
import org.folio.entitlement.integration.interceptor.TokenRefreshRequestInterceptor;
import org.folio.entitlement.integration.token.AdminTokenProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.openfeign.FeignClientsConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.validation.annotation.Validated;

@Data
@Log4j2
@Validated
@Configuration
@Import(FeignClientsConfiguration.class)
@ConfigurationProperties(prefix = "application.am")
public class AppManagerClientConfiguration {

  private String url;

  private TlsProperties tls;

  @Bean
  public ApplicationManagerClient applicationManagerClient(OkHttpClient okHttpClient, Contract contract,
    Encoder encoder, Decoder decoder, RequestInterceptor applicationManagerRequestInterceptor) {
    var okHttpFeignClient = FeignClientTlsUtils.getOkHttpClient(okHttpClient, tls);
    return Feign.builder()
      .client(okHttpFeignClient)
      .contract(contract)
      .encoder(encoder)
      .decoder(decoder)
      .requestInterceptor(applicationManagerRequestInterceptor)
      .target(ApplicationManagerClient.class, url);
  }

  @Bean
  public RequestInterceptor applicationManagerRequestInterceptor(AdminTokenProvider adminTokenProvider) {
    return new TokenRefreshRequestInterceptor(adminTokenProvider);
  }
}
