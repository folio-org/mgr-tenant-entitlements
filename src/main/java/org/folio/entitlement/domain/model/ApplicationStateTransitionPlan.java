package org.folio.entitlement.domain.model;

import static java.util.function.Predicate.not;

import java.util.stream.Stream;

public record ApplicationStateTransitionPlan(
  ApplicationStateTransitionBucket entitleBucket,
  ApplicationStateTransitionBucket upgradeBucket,
  ApplicationStateTransitionBucket revokeBucket) {

  public Stream<ApplicationStateTransitionBucket> nonEmptyBuckets() {
    return Stream.of(entitleBucket, upgradeBucket, revokeBucket)
      .filter(not(ApplicationStateTransitionBucket::isEmpty));
  }
}
