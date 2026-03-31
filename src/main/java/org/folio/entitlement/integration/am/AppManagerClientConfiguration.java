package org.folio.entitlement.integration.am;

import static org.folio.common.utils.tls.HttpClientTlsUtils.buildHttpServiceClient;

import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.folio.common.configuration.properties.TlsProperties;
import org.folio.entitlement.integration.interceptor.TokenRefreshRequestInterceptor;
import org.folio.entitlement.integration.token.AdminTokenProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestClient;

@Data
@Log4j2
@Validated
@Configuration
@ConfigurationProperties(prefix = "application.am")
public class AppManagerClientConfiguration {

  private String url;

  private TlsProperties tls;

  @Bean
  public ApplicationManagerClient applicationManagerClient(AdminTokenProvider adminTokenProvider) {
    var interceptor = new TokenRefreshRequestInterceptor(adminTokenProvider);
    return buildHttpServiceClient(
      RestClient.builder().baseUrl(url).requestInterceptor(interceptor),
      tls, url, ApplicationManagerClient.class);
  }
}
