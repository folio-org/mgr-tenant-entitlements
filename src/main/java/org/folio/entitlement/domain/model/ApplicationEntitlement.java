package org.folio.entitlement.domain.model;

import static java.util.Objects.requireNonNull;

import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.dto.EntitlementType;

public record ApplicationEntitlement(EntitlementType type, ApplicationDescriptor descriptor) {

  public ApplicationEntitlement {
    requireNonNull(type, "Entitlement type must not be null");
    requireNonNull(descriptor, "Application descriptor must not be null");
  }

  public String applicationId() {
    return descriptor.getId();
  }

  public static ApplicationEntitlement entitle(ApplicationDescriptor descriptor) {
    return new ApplicationEntitlement(EntitlementType.ENTITLE, descriptor);
  }

  public static ApplicationEntitlement upgrade(ApplicationDescriptor descriptor) {
    return new ApplicationEntitlement(EntitlementType.UPGRADE, descriptor);
  }

  public static ApplicationEntitlement revoke(ApplicationDescriptor descriptor) {
    return new ApplicationEntitlement(EntitlementType.REVOKE, descriptor);
  }
}
