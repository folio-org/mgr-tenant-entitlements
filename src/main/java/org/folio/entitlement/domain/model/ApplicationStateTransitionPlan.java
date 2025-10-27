package org.folio.entitlement.domain.model;

public record ApplicationStateTransitionPlan(
  ApplicationStateTransitionBucket entitleBucket,
  ApplicationStateTransitionBucket upgradeBucket,
  ApplicationStateTransitionBucket revokeBucket) {
}
