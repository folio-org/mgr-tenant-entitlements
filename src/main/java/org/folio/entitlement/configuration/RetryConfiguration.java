package org.folio.entitlement.configuration;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.folio.entitlement.utils.FlowUtils.addErrorInformation;

import jakarta.ws.rs.WebApplicationException;
import java.util.function.Predicate;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.service.stage.ThreadLocalModuleStageContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
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
      integrationException -> integrationException.getCauseHttpStatus() != null && (
        integrationException.getCauseHttpStatus() >= 500 || integrationException.getCauseHttpStatus() >= 400),
      configProps.getModule().getMax(), configProps.getModule().getBackoff().getDelay(),
      configProps.getModule().getBackoff().getMaxdelay(), configProps.getModule().getBackoff().getMultiplier(),
      "Folio Module call", threadLocalModuleStageContext);
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
      webAppException -> webAppException.getResponse() != null && webAppException.getResponse().getStatus() >= 500,
      configProps.getKeycloak().getMax(), configProps.getKeycloak().getBackoff().getDelay(),
      configProps.getKeycloak().getBackoff().getMaxdelay(), configProps.getKeycloak().getBackoff().getMultiplier(),
      "Keycloak access", threadLocalModuleStageContext);
  }

  private <T extends Exception> RetryOperationsInterceptor createRetryInterceptor(Class<T> exceptionClass,
    Predicate<T> shouldRetry, int numberOfRetries, int backOffDelay, int backOffMaxDelay, int backOffMultiplier,
    String operationDescription, ThreadLocalModuleStageContext threadLocalModuleStageContext) {
    var retryTemplate = new RetryTemplate();
    var retryPolicy = createRetryPolicy(exceptionClass, shouldRetry, numberOfRetries, operationDescription,
      threadLocalModuleStageContext);
    retryTemplate.setRetryPolicy(retryPolicy);
    retryTemplate.setBackOffPolicy(
      BackOffPolicyBuilder.newBuilder().delay(backOffDelay).maxDelay(backOffMaxDelay).multiplier(backOffMultiplier)
        .build());
    var result = new RetryOperationsInterceptor();
    result.setRetryOperations(retryTemplate);
    return result;
  }

  private <T extends Exception> @NotNull SimpleRetryPolicy createRetryPolicy(Class<T> exceptionClass,
    Predicate<T> shouldRetry, int numberOfRetries, String operationDescription,
    ThreadLocalModuleStageContext threadLocalModuleStageContext) {
    var retryPolicy = new SimpleRetryPolicy() {
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
        // If there was no error so far (first attempt) or error is what we allow to retry - return true
        if (exceptionClass.isAssignableFrom(error.getClass()) && shouldRetry.test((T) error)) {
          log.error(String.format("Error occurred for %s - retrying", operationDescription), error);
          addErrorInformation(getStackTrace(error), threadLocalModuleStageContext);
          return true;
        }
        return false;
      }
    };
    retryPolicy.setMaxAttempts(numberOfRetries);
    return retryPolicy;
  }
}
