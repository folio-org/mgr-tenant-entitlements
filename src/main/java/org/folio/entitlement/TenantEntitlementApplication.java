package org.folio.entitlement;

import org.folio.common.service.TransactionHelper;
import org.folio.security.EnableMgrSecurity;
import org.folio.spring.cql.JpaCqlConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@Import({JpaCqlConfiguration.class, TransactionHelper.class})
@EnableMgrSecurity
@ConfigurationPropertiesScan
@EnableScheduling
public class TenantEntitlementApplication {

  public static void main(String[] args) {
    SpringApplication.run(TenantEntitlementApplication.class, args);
  }
}
