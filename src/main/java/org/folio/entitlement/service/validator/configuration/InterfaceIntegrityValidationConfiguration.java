package org.folio.entitlement.service.validator.configuration;

import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.entitlement.service.validator.ApplicationAndDependencyDescriptorProvider;
import org.folio.entitlement.service.validator.ApplicationAndEntitledDescriptorProvider;
import org.folio.entitlement.service.validator.ApplicationInterfaceCollector;
import org.folio.entitlement.service.validator.CombinedApplicationInterfaceCollector;
import org.folio.entitlement.service.validator.InterfaceIntegrityValidator;
import org.folio.entitlement.service.validator.ScopedApplicationInterfaceCollector;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Log4j2
@Configuration
public class InterfaceIntegrityValidationConfiguration {

  @Bean(name = "entitlementApplicationInterfaceCollector")
  @ConditionalOnProperty(name = "application.validation.interface-integrity.interface-collector.mode",
    havingValue = "combined")
  public ApplicationInterfaceCollector combinedApplicationInterfaceCollector(
    EntitlementCrudService entitlementCrudService,
    ApplicationInterfaceCollectorProperties collectorProperties) {

    return new CombinedApplicationInterfaceCollector(entitlementCrudService, collectorProperties);
  }

  @Bean(name = "entitlementApplicationInterfaceCollector")
  @ConditionalOnProperty(name = "application.validation.interface-integrity.interface-collector.mode",
    havingValue = "scoped", matchIfMissing = true)
  public ApplicationInterfaceCollector scopedApplicationInterfaceCollector(
    EntitlementCrudService entitlementCrudService,
    ApplicationInterfaceCollectorProperties collectorProperties) {

    return new ScopedApplicationInterfaceCollector(entitlementCrudService, collectorProperties);
  }

  @Bean
  public InterfaceIntegrityValidator entitlementInterfaceIntegrityValidator(
      InterfaceIntegrityValidatorProperties properties,
      @Qualifier("entitlementApplicationInterfaceCollector") ApplicationInterfaceCollector interfaceCollector,
      ApplicationAndDependencyDescriptorProvider applicationDescriptorProvider) {

    return new InterfaceIntegrityValidator(
        EntitlementType.ENTITLE,
        interfaceCollector,
        applicationDescriptorProvider,
        properties);
  }

  @Bean
  public InterfaceIntegrityValidator upgradeInterfaceIntegrityValidator(
    InterfaceIntegrityValidatorProperties properties,
    @Qualifier("upgradeApplicationInterfaceCollector") ApplicationInterfaceCollector interfaceCollector,
    ApplicationAndEntitledDescriptorProvider applicationDescriptorProvider) {

    return new InterfaceIntegrityValidator(
      EntitlementType.UPGRADE,
      interfaceCollector,
      applicationDescriptorProvider,
      properties);
  }
}
