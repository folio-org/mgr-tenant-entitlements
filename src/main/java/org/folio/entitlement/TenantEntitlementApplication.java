package org.folio.entitlement;

import org.folio.common.service.TransactionHelper;
import org.folio.integration.kafka.EnableKafka;
import org.folio.security.EnableMgrSecurity;
import org.folio.spring.cql.JpaCqlConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@EnableKafka
@EnableFeignClients
@SpringBootApplication
@Import({JpaCqlConfiguration.class, TransactionHelper.class})
@EnableMgrSecurity
public class TenantEntitlementApplication {

  public static void main(String[] args) {
    SpringApplication.run(TenantEntitlementApplication.class, args);
  }
}
