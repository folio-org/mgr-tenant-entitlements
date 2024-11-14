package org.folio.entitlement.retry.feign;

import static org.folio.entitlement.utils.FlowUtils.addErrorInformation;

import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.entitlement.service.stage.ThreadLocalModuleStageContext;

@RequiredArgsConstructor
@Log4j2
public class FeignRetrySupportingErrorDecoder implements ErrorDecoder {

  private final ErrorDecoder delegate;
  private final Predicate<Pair<String, Response>> isRetryable;
  private final String operationDescription;
  private final String errorDescription;
  private final ThreadLocalModuleStageContext threadLocalModuleStageContext;

  @Override
  public Exception decode(String methodKey, Response response) {
    if (isRetryable.test(Pair.of(methodKey, response))) {
      var errorInformation =
        String.format("Error %s occurred for %s - retrying", errorDescription, operationDescription);
      log.error(errorInformation);
      addErrorInformation(errorInformation, threadLocalModuleStageContext);
      return new RetryableException(response.status(), errorDescription, response.request().httpMethod(),
        System.currentTimeMillis(), response.request());
    }

    return delegate.decode(methodKey, response);
  }
}
