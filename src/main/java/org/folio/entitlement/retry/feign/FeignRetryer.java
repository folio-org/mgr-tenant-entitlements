package org.folio.entitlement.retry.feign;

import feign.RetryableException;
import feign.Retryer;
import java.util.function.Predicate;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class FeignRetryer extends Retryer.Default {

  private final Predicate<RetryableException> shouldRetry;
  private final String operationDescription;
  private final String errorDescription;

  public FeignRetryer(long period, long maxPeriod, int maxAttempts, Predicate<RetryableException> shouldRetry,
    String operationDescription, String errorDescription) {
    super(period, maxPeriod, maxAttempts);
    this.shouldRetry = shouldRetry;
    this.operationDescription = operationDescription;
    this.errorDescription = errorDescription;
  }

  @Override
  public void continueOrPropagate(RetryableException e) {
    if (!shouldRetry.test(e)) {
      throw e;
    }
    log.error(String.format("Error %s occurred for %s - retrying", errorDescription, operationDescription));
    super.continueOrPropagate(e);
  }
}
