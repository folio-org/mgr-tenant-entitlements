package org.folio.entitlement.configuration;

import jakarta.ws.rs.WebApplicationException;
import java.util.function.Predicate;
import org.folio.entitlement.integration.IntegrationException;
import org.jetbrains.annotations.NotNull;
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

  /**
   * Create a RetryOperationsInterceptor that will intercept error during calls to folio modules.
   *
   * @return RetryOperationsInterceptor for folio modules calls
   */
  @Bean
  public RetryOperationsInterceptor folioModuleCallsRetryInterceptor(RetryConfigurationProperties configProps) {
    return createRetryInterceptor(IntegrationException.class,
      integrationException -> integrationException.getCauseHttpStatus() != null && (
        integrationException.getCauseHttpStatus() >= 500 || integrationException.getCauseHttpStatus() >= 400),
      configProps.getModule().getMax(), configProps.getModule().getBackoff().getDelay(),
      configProps.getModule().getBackoff().getMaxdelay(), configProps.getModule().getBackoff().getMultiplier());
  }

  /**
   * Create a RetryOperationsInterceptor that will intercept error during calls to Keycloak.
   *
   * @return RetryOperationsInterceptor for Keycloak calls
   */
  @Bean
  public RetryOperationsInterceptor keycloakCallsRetryInterceptor(RetryConfigurationProperties configProps) {
    return createRetryInterceptor(WebApplicationException.class,
      webAppException -> webAppException.getResponse() != null && webAppException.getResponse().getStatus() >= 500,
      configProps.getKeycloak().getMax(), configProps.getKeycloak().getBackoff().getDelay(),
      configProps.getKeycloak().getBackoff().getMaxdelay(), configProps.getKeycloak().getBackoff().getMultiplier());
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
