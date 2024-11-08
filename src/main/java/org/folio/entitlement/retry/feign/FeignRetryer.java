package org.folio.entitlement.retry.feign;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.folio.entitlement.utils.FlowUtils.addErrorInformation;

import feign.RetryableException;
import feign.Retryer;
import java.util.function.Predicate;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.service.RetryInformationService;
import org.folio.entitlement.service.stage.ThreadLocalModuleStageContext;

@Log4j2
public class FeignRetryer extends Retryer.Default {

  private final Predicate<RetryableException> shouldRetry;
  private final String operationDescription;
  private final String errorDescription;
  private final ThreadLocalModuleStageContext threadLocalModuleStageContext;
  private final RetryInformationService retryInformationService;

  public FeignRetryer(long period, long maxPeriod, int maxAttempts, Predicate<RetryableException> shouldRetry,
    String operationDescription, String errorDescription, ThreadLocalModuleStageContext threadLocalModuleStageContext,
    RetryInformationService retryInformationService) {
    super(period, maxPeriod, maxAttempts);
    this.shouldRetry = shouldRetry;
    this.operationDescription = operationDescription;
    this.errorDescription = errorDescription;
    this.threadLocalModuleStageContext = threadLocalModuleStageContext;
    this.retryInformationService = retryInformationService;
  }

  @Override
  public void continueOrPropagate(RetryableException error) {
    if (!shouldRetry.test(error)) {
      throw error;
    }
    addErrorInformation(getStackTrace(error), threadLocalModuleStageContext, retryInformationService);
    log.error(String.format("Error %s occurred for %s - retrying", errorDescription, operationDescription));
    super.continueOrPropagate(error);
  }
}
