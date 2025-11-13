package org.folio.entitlement.domain.model;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.Value;
import org.folio.entitlement.domain.dto.EntitlementType;

@Value
public final class ApplicationStateTransitionBucket {

  EntitlementType entitlementType;
  Set<String> applicationIds = new HashSet<>();

  private ApplicationStateTransitionBucket(EntitlementType entitlementType, Collection<String> applicationIds) {
    requireNonNull(entitlementType, "Entitlement type must not be null");
    this.entitlementType = entitlementType;
    this.applicationIds.addAll(emptyIfNull(applicationIds));
  }

  public static ApplicationStateTransitionBucket entitle(Collection<String> applicationIds) {
    return new ApplicationStateTransitionBucket(ENTITLE, applicationIds);
  }

  public static ApplicationStateTransitionBucket upgrade(Collection<String> applicationIds) {
    return new ApplicationStateTransitionBucket(UPGRADE, applicationIds);
  }

  public static ApplicationStateTransitionBucket revoke(Collection<String> applicationIds) {
    return new ApplicationStateTransitionBucket(REVOKE, applicationIds);
  }

  public Set<String> getApplicationIds() {
    return Set.copyOf(applicationIds);
  }

  public boolean isEmpty() {
    return applicationIds.isEmpty();
  }
}
