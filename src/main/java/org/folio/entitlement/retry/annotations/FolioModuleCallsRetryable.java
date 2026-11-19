package org.folio.entitlement.retry.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a class or a method whose calls to FOLIO modules are retried on failure.
 *
 * <p>A marker only: the retry itself is applied by the advisor declared in
 * {@link org.folio.entitlement.configuration.RetryConfiguration}, using the {@code retries.module.*} settings.
 * The method must be invoked through the Spring proxy (not via {@code this}) for the retry to apply.</p>
 */
@Documented
@Retention(RUNTIME)
@Target({METHOD, TYPE})
public @interface FolioModuleCallsRetryable {

}
