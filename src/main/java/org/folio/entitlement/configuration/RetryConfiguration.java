package org.folio.entitlement.configuration;

import jakarta.ws.rs.WebApplicationException;
import java.util.function.Predicate;
import org.folio.entitlement.integration.IntegrationException;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.BackOffPolicyBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@EnableRetry
@Configuration
public class RetryConfiguration {

  @Value("${retries.module.max:3}") private int maxRetriesForFolioModuleCalls = 3;
  @Value("${retries.keycloak.max:3}") private int maxRetriesForKeycloakCalls = 3;

  @Value("${retries.module.backoff.delay:1000}") private int backOffDelayForFolioModuleCalls = 1000;
  @Value("${retries.module.backoff.maxdelay:30000}") private int backOffMaxDelayForFolioModuleCalls = 30000;
  @Value("${retries.module.backoff.multiplier:5}") private int backOffMultiplierForFolioModuleCalls = 5;

  @Value("${retries.keycloak.backoff.delay:1000}") private int backOffDelayForKeycloakCalls = 1000;
  @Value("${retries.keycloak.backoff.maxdelay:30000}") private int backOffMaxDelayForKeycloakCalls = 30000;
  @Value("${retries.keycloak.backoff.multiplier:5}") private int backOffMultiplierForKeycloakCalls = 5;

  /**
   * Create a RetryOperationsInterceptor that will intercept error during calls to folio modules.
   *
   * @return RetryOperationsInterceptor for folio modules calls
   */
  @Bean
  public RetryOperationsInterceptor folioModuleCallsRetryInterceptor() {
    return createRetryInterceptor(IntegrationException.class,
      integrationException -> integrationException.getCauseHttpStatus() != null
        && integrationException.getCauseHttpStatus() >= 500, maxRetriesForFolioModuleCalls,
      backOffDelayForFolioModuleCalls, backOffMaxDelayForFolioModuleCalls, backOffMultiplierForFolioModuleCalls);
  }

  /**
   * Create a RetryOperationsInterceptor that will intercept error during calls to Keycloak.
   *
   * @return RetryOperationsInterceptor for Keycloak calls
   */
  @Bean
  public RetryOperationsInterceptor keycloakCallsRetryInterceptor() {
    return createRetryInterceptor(WebApplicationException.class,
      webAppException -> webAppException.getResponse() != null && webAppException.getResponse().getStatus() >= 500,
      maxRetriesForKeycloakCalls, backOffDelayForKeycloakCalls, backOffMaxDelayForKeycloakCalls,
      backOffMultiplierForKeycloakCalls);
  }

  private static <T extends Exception> RetryOperationsInterceptor createRetryInterceptor(Class<T> exceptionClass,
    Predicate<T> shouldRetry, int numberOfRetries, int backOffDelay, int backOffMaxDelay, int backOffMultiplier) {
    var retryTemplate = new RetryTemplate();
    var retryPolicy = createRetryPolicy(exceptionClass, shouldRetry, numberOfRetries);
    retryTemplate.setRetryPolicy(retryPolicy);
    retryTemplate.setBackOffPolicy(
      BackOffPolicyBuilder.newBuilder().delay(backOffDelay).maxDelay(backOffMaxDelay).multiplier(backOffMultiplier)
        .build());
    var result = new RetryOperationsInterceptor();
    result.setRetryOperations(retryTemplate);
    return result;
  }

  private static <T extends Exception> @NotNull SimpleRetryPolicy createRetryPolicy(Class<T> exceptionClass,
    Predicate<T> shouldRetry, int numberOfRetries) {
    var retryPolicy = new SimpleRetryPolicy() {
      @Override
      @SuppressWarnings("unchecked")
      public boolean canRetry(RetryContext context) {
        var error = context.getLastThrowable();
        if (context.getRetryCount() >= numberOfRetries) {
          return false;
        }
        // If there was no error so far (first attempt) or error is what we allow to retry - retun true
        return error == null || exceptionClass.isAssignableFrom(error.getClass()) && shouldRetry.test((T) error);
      }
    };
    retryPolicy.setMaxAttempts(numberOfRetries);
    return retryPolicy;
  }
}
