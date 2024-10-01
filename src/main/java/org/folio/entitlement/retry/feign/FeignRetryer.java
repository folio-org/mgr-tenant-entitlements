package org.folio.entitlement.retry.feign;

import feign.RetryableException;
import feign.Retryer;
import java.util.function.Predicate;

public class FeignRetryer extends Retryer.Default {

  private final long period;
  private final long maxPeriod;
  private final int maxAttempts;
  private final Predicate<RetryableException> shouldRetry;

  public FeignRetryer(long period, long maxPeriod, int maxAttempts, Predicate<RetryableException> shouldRetry) {
    super(period, maxPeriod, maxAttempts);
    this.period = period;
    this.maxPeriod = maxPeriod;
    this.maxAttempts = maxAttempts;
    this.shouldRetry = shouldRetry;
  }

  @Override
  public void continueOrPropagate(RetryableException e) {
    if (!shouldRetry.test(e)) {
      throw e;
    }
    super.continueOrPropagate(e);
  }
}
