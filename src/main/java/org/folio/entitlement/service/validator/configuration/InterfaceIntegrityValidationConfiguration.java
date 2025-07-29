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
import org.folio.entitlement.service.validator.StageRequestValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Log4j2
public class InterfaceIntegrityValidationConfiguration {

  private static final String PROP_ENTITLEMENT_VALIDATION_ENABLED =
    "application.validation.interface-integrity.entitlement.enabled";
  private static final String PROP_UPGRADE_VALIDATION_ENABLED =
    "application.validation.interface-integrity.upgrade.enabled";

  @Configuration
  @ConditionalOnProperty(
    name = PROP_ENTITLEMENT_VALIDATION_ENABLED,
    havingValue = "true", matchIfMissing = true)
  public static class EnabledValidationForEntitlement {

    private static final String PROP_INTERFACE_COLLECTOR_MODE =
      "application.validation.interface-integrity.entitlement.interface-collector.mode";
    private static final String VAL_EXCLUDE_REQUIRED_INTERFACES_OF_ENTITLED_APPS =
      "${application.validation.interface-integrity.entitlement.interface-collector.required.exclude-entitled}";

    @Bean(name = "entitlementApplicationInterfaceCollector")
    @ConditionalOnProperty(
      name = PROP_INTERFACE_COLLECTOR_MODE,
      havingValue = "combined")
    public ApplicationInterfaceCollector combinedApplicationInterfaceCollectorForEntitlement(
      EntitlementCrudService entitlementCrudService,
      @Value(VAL_EXCLUDE_REQUIRED_INTERFACES_OF_ENTITLED_APPS) boolean excludeEntitled) {

      return new CombinedApplicationInterfaceCollector(entitlementCrudService, excludeEntitled);
    }

    @Bean(name = "entitlementApplicationInterfaceCollector")
    @ConditionalOnProperty(
      name = PROP_INTERFACE_COLLECTOR_MODE,
      havingValue = "scoped", matchIfMissing = true)
    public ApplicationInterfaceCollector scopedApplicationInterfaceCollectorForEntitlement(
      EntitlementCrudService entitlementCrudService,
      @Value(VAL_EXCLUDE_REQUIRED_INTERFACES_OF_ENTITLED_APPS) boolean excludeEntitled) {

      return new ScopedApplicationInterfaceCollector(entitlementCrudService, excludeEntitled);
    }

    @Bean
    public InterfaceIntegrityValidator entitlementInterfaceIntegrityValidator(
      @Qualifier("entitlementApplicationInterfaceCollector") ApplicationInterfaceCollector interfaceCollector,
      ApplicationAndDependencyDescriptorProvider applicationDescriptorProvider) {

      return new InterfaceIntegrityValidator(
        EntitlementType.ENTITLE,
        interfaceCollector,
        applicationDescriptorProvider);
    }
  }

  @Configuration
  @ConditionalOnProperty(
    name = PROP_ENTITLEMENT_VALIDATION_ENABLED,
    havingValue = "false")
  public static class DisabledValidationForEntitlement {

    @Bean
    public StageRequestValidator entitlementInterfaceIntegrityValidator(){
      return InterfaceIntegrityValidator.NO_OP;
    }
  }

  @Configuration
  @ConditionalOnProperty(
    name = PROP_UPGRADE_VALIDATION_ENABLED,
    havingValue = "true", matchIfMissing = true)
  public static class EnabledValidationForUpgrade {

    @Bean(name = "upgradeApplicationInterfaceCollector")
    public ApplicationInterfaceCollector combinedApplicationInterfaceCollectorForUpgrade(
      EntitlementCrudService entitlementCrudService) {

      return new CombinedApplicationInterfaceCollector(entitlementCrudService, false);
    }

    @Bean
    public InterfaceIntegrityValidator upgradeInterfaceIntegrityValidator(
      @Qualifier("upgradeApplicationInterfaceCollector") ApplicationInterfaceCollector interfaceCollector,
      ApplicationAndEntitledDescriptorProvider applicationDescriptorProvider) {

      return new InterfaceIntegrityValidator(
        EntitlementType.UPGRADE,
        interfaceCollector,
        applicationDescriptorProvider);
    }
  }

  @Configuration
  @ConditionalOnProperty(
    name = PROP_UPGRADE_VALIDATION_ENABLED,
    havingValue = "false")
  public static class DisabledValidationForUpgrade {

    @Bean
    public StageRequestValidator upgradeInterfaceIntegrityValidator(){
      return InterfaceIntegrityValidator.NO_OP;
    }
  }
}
