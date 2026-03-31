package org.folio.entitlement.configuration;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.folio.entitlement.utils.FlowUtils.addErrorInformation;

import jakarta.ws.rs.WebApplicationException;
import java.util.function.Predicate;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.configuration.RetryConfigurationProperties.RetryBackoffConfigProps;
import org.folio.entitlement.configuration.RetryConfigurationProperties.RetryConfigProps;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.service.stage.ThreadLocalModuleStageContext;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.BackOffPolicyBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@EnableRetry
@Configuration
@Log4j2
public class RetryConfiguration {

  /**
   * Create a RetryOperationsInterceptor that will intercept error during calls to folio modules.
   *
   * @return RetryOperationsInterceptor for folio modules calls
   */
  @Bean
  public RetryOperationsInterceptor folioModuleCallsRetryInterceptor(RetryConfigurationProperties configProps,
    ThreadLocalModuleStageContext threadLocalModuleStageContext) {
    return createRetryInterceptor(IntegrationException.class,
      integrationError(),
      configProps.getModule(), "Folio Module call",
      threadLocalModuleStageContext);
  }

  /**
   * Create a RetryOperationsInterceptor that will intercept error during calls to Keycloak.
   *
   * @return RetryOperationsInterceptor for Keycloak calls
   */
  @Bean
  public RetryOperationsInterceptor keycloakCallsRetryInterceptor(RetryConfigurationProperties configProps,
    ThreadLocalModuleStageContext threadLocalModuleStageContext) {
    return createRetryInterceptor(WebApplicationException.class,
      webApplicationError(),
      configProps.getKeycloak(), "Keycloak access",
      threadLocalModuleStageContext);
  }

  private static @NonNull Predicate<IntegrationException> integrationError() {
    return exception -> exception.getCauseHttpStatus() != null && exception.getCauseHttpStatus() >= 400;
  }

  private static @NonNull Predicate<WebApplicationException> webApplicationError() {
    return exception -> exception.getResponse() != null && exception.getResponse().getStatus() >= 500;
  }

  private <T extends Exception> RetryOperationsInterceptor createRetryInterceptor(Class<T> exceptionClass,
    Predicate<T> shouldRetry, RetryConfigProps retryConfig, String operationDescription,
    ThreadLocalModuleStageContext threadLocalModuleStageContext) {
    var retryTemplate = new RetryTemplate();

    retryTemplate.setRetryPolicy(createRetryPolicy(exceptionClass, shouldRetry, retryConfig.getMax(),
      operationDescription, threadLocalModuleStageContext));
    retryTemplate.setBackOffPolicy(createBackOffPolicy(retryConfig.getBackoff()));

    var result = new RetryOperationsInterceptor();
    result.setRetryOperations(retryTemplate);

    return result;
  }

  private static <T extends Exception> @NonNull SimpleRetryPolicy createRetryPolicy(Class<T> exceptionClass,
    Predicate<T> shouldRetry, int numberOfRetries, String operationDescription,
    ThreadLocalModuleStageContext threadLocalModuleStageContext) {
    var retryPolicy = new CustomRetryPolicy<>(numberOfRetries, exceptionClass, shouldRetry, operationDescription,
      threadLocalModuleStageContext);
    retryPolicy.setMaxAttempts(numberOfRetries);
    return retryPolicy;
  }

  private static BackOffPolicy createBackOffPolicy(RetryBackoffConfigProps configProps) {
    return BackOffPolicyBuilder.newBuilder().delay(configProps.getDelay())
      .maxDelay(configProps.getMaxdelay())
      .multiplier(configProps.getMultiplier()).build();
  }

  private static class CustomRetryPolicy<T extends Exception> extends SimpleRetryPolicy {
    private final int numberOfRetries;
    private final Class<T> exceptionClass;
    private final Predicate<T> shouldRetry;
    private final String operationDescription;
    private final ThreadLocalModuleStageContext threadLocalModuleStageContext;

    CustomRetryPolicy(int numberOfRetries, Class<T> exceptionClass, Predicate<T> shouldRetry,
      String operationDescription, ThreadLocalModuleStageContext threadLocalModuleStageContext) {
      this.numberOfRetries = numberOfRetries;
      this.exceptionClass = exceptionClass;
      this.shouldRetry = shouldRetry;
      this.operationDescription = operationDescription;
      this.threadLocalModuleStageContext = threadLocalModuleStageContext;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean canRetry(RetryContext context) {
      var error = context.getLastThrowable();
      if (context.getRetryCount() >= numberOfRetries) {
        return false;
      }
      if (error == null) {
        // If there was no error so far (first attempt) - return true
        return true;
      }
      // If there was an error that we can retry - return true
      return exceptionClass.isAssignableFrom(error.getClass()) && shouldRetry.test((T) error);
    }

    @Override
    public void registerThrowable(RetryContext context, Throwable throwable) {
      super.registerThrowable(context, throwable);
      log.error(String.format("Error occurred for %s - retrying", operationDescription), throwable);
      addErrorInformation(getStackTrace(throwable), threadLocalModuleStageContext);
    }
  }
}
