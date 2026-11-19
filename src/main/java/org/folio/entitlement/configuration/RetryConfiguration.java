package org.folio.entitlement.configuration;

import jakarta.ws.rs.WebApplicationException;
import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.List;
import org.folio.entitlement.configuration.RetryConfigurationProperties.RetryConfigProps;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.retry.annotations.FolioModuleCallsRetryable;
import org.folio.entitlement.retry.annotations.KeycloakCallsRetryable;
import org.folio.entitlement.retry.predicate.FolioModuleCallsRetryPredicate;
import org.folio.entitlement.retry.predicate.KeycloakCallsRetryPredicate;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Role;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.resilience.retry.MethodRetryPredicate;
import org.springframework.resilience.retry.MethodRetrySpec;
import org.springframework.resilience.retry.SimpleRetryInterceptor;

/**
 * Applies Spring Framework retry to beans annotated with {@link FolioModuleCallsRetryable} or
 * {@link KeycloakCallsRetryable}.
 *
 * <p>{@link EnableAspectJAutoProxy} registers the auto-proxy creator that wraps matching beans with the
 * {@link Advisor} beans declared here; without it the advisors are never applied and annotated methods are not
 * retried. {@code proxyTargetClass = true} forces class-based proxies, because the annotated beans are injected by
 * their concrete class and a JDK interface proxy is not an instance of it. Spring Boot's {@code AopAutoConfiguration}
 * registers the same creator, but it is not active in tests that load this class on its own, so the annotation is kept
 * to make the configuration self-contained.</p>
 */
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class RetryConfiguration {

  /**
   * Creates an interceptor that retries failed calls to FOLIO modules.
   *
   * @return retry interceptor for FOLIO module calls
   */
  @Bean
  public SimpleRetryInterceptor folioModuleCallsRetryInterceptor(RetryConfigurationProperties configProps) {
    var spec = retrySpec(IntegrationException.class, new FolioModuleCallsRetryPredicate(), configProps.getModule());
    return new SimpleRetryInterceptor(spec);
  }

  /**
   * Creates an interceptor that retries failed calls to Keycloak.
   *
   * @return retry interceptor for Keycloak calls
   */
  @Bean
  public SimpleRetryInterceptor keycloakCallsRetryInterceptor(RetryConfigurationProperties configProps) {
    var spec = retrySpec(WebApplicationException.class, new KeycloakCallsRetryPredicate(), configProps.getKeycloak());
    return new SimpleRetryInterceptor(spec);
  }

  /**
   * Creates an advisor that applies the FOLIO module retry interceptor to beans and methods annotated with
   * {@link FolioModuleCallsRetryable}.
   *
   * @return advisor for FOLIO module calls
   */
  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public Advisor folioModuleCallsRetryAdvisor(RetryConfigurationProperties configProps) {
    return new DefaultPointcutAdvisor(annotatedWith(FolioModuleCallsRetryable.class),
      folioModuleCallsRetryInterceptor(configProps));
  }

  /**
   * Creates an advisor that applies the Keycloak retry interceptor to beans and methods annotated with
   * {@link KeycloakCallsRetryable}.
   *
   * @return advisor for Keycloak calls
   */
  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public Advisor keycloakCallsRetryAdvisor(RetryConfigurationProperties configProps) {
    return new DefaultPointcutAdvisor(annotatedWith(KeycloakCallsRetryable.class),
      keycloakCallsRetryInterceptor(configProps));
  }

  /**
   * Maps the configured number of attempts to a Spring {@link MethodRetrySpec}, which counts retries (attempts - 1).
   *
   * @param exceptionType exception type that can be retried
   * @param predicate additional condition a failure must satisfy to be retried
   * @param config configured attempts and back-off
   * @return validated retry specification
   * @throws IllegalArgumentException if the configured values are rejected by the retry policy
   */
  static MethodRetrySpec retrySpec(Class<? extends Throwable> exceptionType, MethodRetryPredicate predicate,
    RetryConfigProps config) {
    var backoff = config.getBackoff();
    var spec = new MethodRetrySpec(List.of(exceptionType), List.of(), predicate,
      Math.max(0, config.getMax() - 1), Duration.ZERO,
      Duration.ofMillis(backoff.getDelay()), Duration.ZERO,
      backoff.getMultiplier(), Duration.ofMillis(backoff.getMaxdelay()));
    validate(spec);
    return spec;
  }

  /**
   * Builds a throwaway {@link RetryPolicy} from the spec, so that invalid values fail at startup rather than on the
   * first retried call.
   */
  private static void validate(MethodRetrySpec spec) {
    RetryPolicy.builder().maxRetries(spec.maxRetries()).timeout(spec.timeout()).delay(spec.delay())
      .jitter(spec.jitter()).multiplier(spec.multiplier()).maxDelay(spec.maxDelay()).build();
  }

  /**
   * Creates a pointcut matching methods of classes annotated with the given annotation, and methods annotated with it.
   *
   * @param annotation marker annotation
   * @return pointcut matching class-level and method-level usages
   */
  private static Pointcut annotatedWith(Class<? extends Annotation> annotation) {
    return new ComposablePointcut(new AnnotationMatchingPointcut(annotation, true))
      .union(new AnnotationMatchingPointcut(null, annotation, true));
  }
}
