package org.folio.entitlement.retry.feign;

import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

@RequiredArgsConstructor
public class FeignRetrySupportingErrorDecoder implements ErrorDecoder {

  private final ErrorDecoder delegate;
  private final Predicate<Pair<String, Response>> isRetryable;

  @Override
  public Exception decode(String methodKey, Response response) {
    if (isRetryable.test(Pair.of(methodKey, response))) {
      return new RetryableException(response.status(), "Internal Server Error", response.request().httpMethod(),
        System.currentTimeMillis(), response.request());
    }

    return delegate.decode(methodKey, response);
  }
}
