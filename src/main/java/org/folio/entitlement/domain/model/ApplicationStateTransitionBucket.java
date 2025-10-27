package org.folio.entitlement.domain.model;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.Value;

@Value
public final class ApplicationStateTransitionBucket {

  ApplicationStateTransitionType transitionType;
  Set<String> applicationIds = new HashSet<>();

  private ApplicationStateTransitionBucket(ApplicationStateTransitionType transitionType,
    Collection<String> applicationIds) {
    this.transitionType = transitionType;
    this.applicationIds.addAll(applicationIds);
  }

  public static ApplicationStateTransitionBucket entitle(Collection<String> applicationIds) {
    return new ApplicationStateTransitionBucket(ApplicationStateTransitionType.ENTITLE, applicationIds);
  }

  public static ApplicationStateTransitionBucket upgrade(Collection<String> applicationIds) {
    return new ApplicationStateTransitionBucket(ApplicationStateTransitionType.UPGRADE, applicationIds);
  }

  public static ApplicationStateTransitionBucket revoke(Collection<String> applicationIds) {
    return new ApplicationStateTransitionBucket(ApplicationStateTransitionType.REVOKE, applicationIds);
  }

  public Set<String> getApplicationIds() {
    return Set.copyOf(applicationIds);
  }

  public boolean isEmpty() {
    return applicationIds.isEmpty();
  }
}
