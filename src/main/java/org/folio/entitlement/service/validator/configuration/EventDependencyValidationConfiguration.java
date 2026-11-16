package org.folio.entitlement.service.validator.configuration;

import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.service.validator.EventDependencyValidator;
import org.folio.entitlement.service.validator.adp.ApplicationAndEntitledDescriptorProvider;
import org.folio.entitlement.service.validator.adp.ApplicationOnlyDescriptorProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates one {@link EventDependencyValidator} per request type.
 * Entitle and upgrade look for producers among the requested applications and all applications entitled to the tenant.
 * Desired state looks among the requested applications.
 */
@Configuration
public class EventDependencyValidationConfiguration {

  private static final String VAL_STRICT = "${application.validation.event-dependency.strict-event-validation:false}";

  @Bean
  public EventDependencyValidator entitlementEventDependencyValidator(
    ApplicationAndEntitledDescriptorProvider descriptorProvider, @Value(VAL_STRICT) boolean strict) {
    return new EventDependencyValidator(EntitlementRequestType.ENTITLE, descriptorProvider, strict);
  }

  @Bean
  public EventDependencyValidator upgradeEventDependencyValidator(
    ApplicationAndEntitledDescriptorProvider descriptorProvider, @Value(VAL_STRICT) boolean strict) {
    return new EventDependencyValidator(EntitlementRequestType.UPGRADE, descriptorProvider, strict);
  }

  @Bean
  public EventDependencyValidator stateEventDependencyValidator(
    ApplicationOnlyDescriptorProvider descriptorProvider, @Value(VAL_STRICT) boolean strict) {
    return new EventDependencyValidator(EntitlementRequestType.STATE, descriptorProvider, strict);
  }
}
