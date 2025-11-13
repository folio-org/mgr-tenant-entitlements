package org.folio.entitlement.service.validator.configuration;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.entitlement.service.validator.InterfaceIntegrityValidator;
import org.folio.entitlement.service.validator.StageRequestValidator;
import org.folio.entitlement.service.validator.adp.ApplicationAndDependencyDescriptorProvider;
import org.folio.entitlement.service.validator.adp.ApplicationAndEntitledDescriptorProvider;
import org.folio.entitlement.service.validator.adp.ApplicationOnlyDescriptorProvider;
import org.folio.entitlement.service.validator.icollector.ApplicationInterfaceCollector;
import org.folio.entitlement.service.validator.icollector.CombinedApplicationInterfaceCollector;
import org.folio.entitlement.service.validator.icollector.ScopedApplicationInterfaceCollector;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class InterfaceIntegrityValidationConfiguration {

  private static final String PROP_ENTITLEMENT_VALIDATION_ENABLED =
    "application.validation.interface-integrity.entitlement.enabled";
  private static final String PROP_UPGRADE_VALIDATION_ENABLED =
    "application.validation.interface-integrity.upgrade.enabled";
  private static final String PROP_STATE_VALIDATION_ENABLED =
    "application.validation.interface-integrity.state.enabled";

  /**
   * Configuration for interface integrity validation for entitlement requests when the validation is enabled.
   * It provides beans for application interface collector and interface integrity validator.
   *
   * <p>The validation is enabled by default, but can be disabled by setting the property
   * {@code application.validation.interface-integrity.entitlement.enabled} to {@code false}.
   *
   * <p>The interface collector can be configured to use either 'combined' or 'scoped' mode
   * by setting the property {@code application.validation.interface-integrity.entitlement.interface-collector.mode}
   * to {@code combined} or {@code scoped}, respectively.
   * The default mode is {@code scoped}.
   *
   * <p>The property
   * {@code application.validation.interface-integrity.entitlement.interface-collector.required.exclude-entitled}
   * can be used to exclude required interfaces of entitled applications from the validation.
   */
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

      log.info("Creating 'combined' interface collector for Entitlement requests with excludeEntitled = {}",
        excludeEntitled);
      return new CombinedApplicationInterfaceCollector(entitlementCrudService, excludeEntitled);
    }

    @Bean(name = "entitlementApplicationInterfaceCollector")
    @ConditionalOnProperty(
      name = PROP_INTERFACE_COLLECTOR_MODE,
      havingValue = "scoped", matchIfMissing = true)
    public ApplicationInterfaceCollector scopedApplicationInterfaceCollectorForEntitlement(
      EntitlementCrudService entitlementCrudService,
      @Value(VAL_EXCLUDE_REQUIRED_INTERFACES_OF_ENTITLED_APPS) boolean excludeEntitled) {

      log.info("Creating 'scoped' interface collector for Entitlement requests with excludeEntitled = {}",
        excludeEntitled);
      return new ScopedApplicationInterfaceCollector(entitlementCrudService, excludeEntitled);
    }

    @Bean
    public InterfaceIntegrityValidator entitlementInterfaceIntegrityValidator(
      @Qualifier("entitlementApplicationInterfaceCollector") ApplicationInterfaceCollector interfaceCollector,
      ApplicationAndDependencyDescriptorProvider applicationDescriptorProvider) {

      return new InterfaceIntegrityValidator(
        EntitlementRequestType.ENTITLE,
        interfaceCollector,
        applicationDescriptorProvider);
    }
  }

  /**
   * Configuration for interface integrity validation for entitlement requests when the validation is disabled.
   * It provides a no-op validator that does not perform any validation.
   *
   * <p>The validation is disabled by setting the property
   * {@code application.validation.interface-integrity.entitlement.enabled} to {@code false}.
   */
  @Configuration
  @ConditionalOnProperty(
    name = PROP_ENTITLEMENT_VALIDATION_ENABLED,
    havingValue = "false")
  public static class DisabledValidationForEntitlement {

    @Bean
    public StageRequestValidator entitlementInterfaceIntegrityValidator() {
      log.info("Interface integrity validation for Entitlement requests is disabled");
      return InterfaceIntegrityValidator.NO_OP;
    }
  }

  /**
   * Configuration for interface integrity validation for upgrade requests when the validation is enabled.
   * It provides beans for application interface collector and interface integrity validator.
   *
   * <p>The validation is enabled by default, but can be disabled by setting the property
   * {@code application.validation.interface-integrity.upgrade.enabled} to {@code false}.
   *
   * <p>The interface collector is always 'combined' for upgrade requests with the turned-off ability to exclude
   * required interfaces of entitled applications from the validation.
   */
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
        EntitlementRequestType.UPGRADE,
        interfaceCollector,
        applicationDescriptorProvider);
    }
  }

  /**
   * Configuration for interface integrity validation for upgrade requests when the validation is disabled.
   * It provides a no-op validator that does not perform any validation.
   *
   * <p>The validation is disabled by setting the property
   * {@code application.validation.interface-integrity.upgrade.enabled} to {@code false}.
   */
  @Configuration
  @ConditionalOnProperty(
    name = PROP_UPGRADE_VALIDATION_ENABLED,
    havingValue = "false")
  public static class DisabledValidationForUpgrade {

    @Bean
    public StageRequestValidator upgradeInterfaceIntegrityValidator() {
      log.info("Interface integrity validation for Upgrade requests is disabled");
      return InterfaceIntegrityValidator.NO_OP;
    }
  }

  /**
   * Configuration for interface integrity validation for desired state requests when the validation is enabled.
   * It provides beans for application interface collector and interface integrity validator.
   *
   * <p>The validation is enabled by default, but can be disabled by setting the property
   * {@code application.validation.interface-integrity.state.enabled} to {@code false}.
   *
   * <p>The interface collector is always 'combined' for desired state requests with the turned-off ability to exclude
   * required interfaces of entitled applications from the validation. Actually, for desired state requests, there is no
   * need to take into account entitled applications only ones provided in the request, and the combined collector
   * provides the necessary functionality when working in this mode.
   */
  @Configuration
  @ConditionalOnProperty(
    name = PROP_STATE_VALIDATION_ENABLED,
    havingValue = "true", matchIfMissing = true)
  public static class EnabledValidationForState {

    @Bean(name = "stateApplicationInterfaceCollector")
    public ApplicationInterfaceCollector combinedApplicationInterfaceCollectorForState(
      EntitlementCrudService entitlementCrudService) {

      return new CombinedApplicationInterfaceCollector(entitlementCrudService, false);
    }

    @Bean
    public InterfaceIntegrityValidator stateInterfaceIntegrityValidator(
      @Qualifier("stateApplicationInterfaceCollector") ApplicationInterfaceCollector interfaceCollector,
      ApplicationOnlyDescriptorProvider applicationDescriptorProvider) {

      return new InterfaceIntegrityValidator(
        EntitlementRequestType.STATE,
        interfaceCollector,
        applicationDescriptorProvider);
    }
  }

  /**
   * Configuration for interface integrity validation for desired state requests when the validation is disabled.
   * It provides a no-op validator that does not perform any validation.
   *
   * <p>The validation is disabled by setting the property
   * {@code application.validation.interface-integrity.state.enabled} to {@code false}.
   */
  @Configuration
  @ConditionalOnProperty(
    name = PROP_STATE_VALIDATION_ENABLED,
    havingValue = "false")
  public static class DisabledValidationForState {

    @Bean
    public StageRequestValidator stateInterfaceIntegrityValidator() {
      log.info("Interface integrity validation for Desired State requests is disabled");
      return InterfaceIntegrityValidator.NO_OP;
    }
  }
}
