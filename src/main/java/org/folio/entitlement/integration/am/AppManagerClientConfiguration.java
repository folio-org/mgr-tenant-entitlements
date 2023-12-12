package org.folio.entitlement.integration.am;

import feign.Contract;
import feign.Feign;
import feign.codec.Decoder;
import feign.codec.Encoder;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
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

  @Bean
  public ApplicationManagerClient applicationManagerClient(Contract contract, Encoder encoder, Decoder decoder) {
    return Feign.builder()
      .contract(contract).encoder(encoder).decoder(decoder)
      .target(ApplicationManagerClient.class, url);
  }
}
