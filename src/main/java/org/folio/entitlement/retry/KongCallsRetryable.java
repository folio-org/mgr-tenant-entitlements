package org.folio.entitlement.retry;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.springframework.retry.annotation.Retryable;

@Documented
@Retention(RUNTIME)
@Target({METHOD, TYPE})
@Retryable(
  interceptor = "kongCallsRetryInterceptor"
)
public @interface KongCallsRetryable {

}
