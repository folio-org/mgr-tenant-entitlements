package org.folio.entitlement.domain.model;

import static java.util.Collections.emptySet;
import static java.util.function.Predicate.not;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.entitlement.domain.model.ApplicationStateTransitionBucket.entitle;
import static org.folio.entitlement.domain.model.ApplicationStateTransitionBucket.revoke;
import static org.folio.entitlement.domain.model.ApplicationStateTransitionBucket.upgrade;

import java.util.Set;
import java.util.stream.Stream;

public record ApplicationStateTransitionPlan(
  ApplicationStateTransitionBucket entitleBucket,
  ApplicationStateTransitionBucket upgradeBucket,
  ApplicationStateTransitionBucket revokeBucket) {

  public ApplicationStateTransitionPlan(ApplicationStateTransitionBucket entitleBucket,
    ApplicationStateTransitionBucket upgradeBucket, ApplicationStateTransitionBucket revokeBucket) {
    this.entitleBucket = entitleBucket != null ? entitleBucket : entitle(emptySet());
    this.upgradeBucket = upgradeBucket != null ? upgradeBucket : upgrade(emptySet());
    this.revokeBucket = revokeBucket != null ? revokeBucket : revoke(emptySet());
  }

  public static ApplicationStateTransitionPlan of(Set<String> entitleIds, Set<String> upgradeIds,
    Set<String> revokeIds) {
    var entitleBucket = isNotEmpty(entitleIds) ? entitle(entitleIds) : null;
    var upgradeBucket = isNotEmpty(upgradeIds) ? upgrade(upgradeIds) : null;
    var revokeBucket = isNotEmpty(revokeIds) ? revoke(revokeIds) : null;
    return new ApplicationStateTransitionPlan(entitleBucket, upgradeBucket, revokeBucket);
  }

  public Stream<ApplicationStateTransitionBucket> nonEmptyBuckets() {
    return Stream.of(entitleBucket, upgradeBucket, revokeBucket)
      .filter(not(ApplicationStateTransitionBucket::isEmpty));
  }
}
