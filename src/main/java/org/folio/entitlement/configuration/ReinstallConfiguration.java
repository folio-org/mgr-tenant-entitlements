package org.folio.entitlement.configuration;

import java.util.concurrent.Executors;
import org.folio.entitlement.integration.folio.FolioModuleService;
import org.folio.entitlement.integration.tm.TenantManagerService;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.entitlement.service.ReinstallService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBean(FolioModuleService.class)
public class ReinstallConfiguration {

  @Bean
  public ReinstallService reinstallService(FolioModuleService folioModuleService,
    TenantManagerService tenantManagerService, ApplicationManagerService applicationManagerService) {
    return new ReinstallService(Executors::newCachedThreadPool, folioModuleService, tenantManagerService,
      applicationManagerService);
  }
}
