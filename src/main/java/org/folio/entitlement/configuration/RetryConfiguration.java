package org.folio.entitlement.configuration;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.folio.entitlement.utils.FlowUtils.addErrorInformation;

import jakarta.ws.rs.WebApplicationException;
import java.util.function.Predicate;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.service.stage.ThreadLocalModuleStageContext;
import org.jetbrains.annotations.NotNull;
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
      integrationException -> integrationException.getCauseHttpStatus() != null
        && integrationException.getCauseHttpStatus() >= 400,
      configProps.getModule().getMax(),
      BackOffPolicyBuilder.newBuilder().delay(configProps.getModule().getBackoff().getDelay())
        .maxDelay(configProps.getModule().getBackoff().getMaxdelay())
        .multiplier(configProps.getModule().getBackoff().getMultiplier()).build(), "Folio Module call",
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
      webAppException -> webAppException.getResponse() != null && webAppException.getResponse().getStatus() >= 500,
      configProps.getKeycloak().getMax(),
      BackOffPolicyBuilder.newBuilder().delay(configProps.getModule().getBackoff().getDelay())
        .maxDelay(configProps.getModule().getBackoff().getMaxdelay())
        .multiplier(configProps.getModule().getBackoff().getMultiplier()).build(), "Keycloak access",
      threadLocalModuleStageContext);
  }

  private <T extends Exception> RetryOperationsInterceptor createRetryInterceptor(Class<T> exceptionClass,
    Predicate<T> shouldRetry, int numberOfRetries, BackOffPolicy backOffPolicy, String operationDescription,
    ThreadLocalModuleStageContext threadLocalModuleStageContext) {
    var retryTemplate = new RetryTemplate();
    var retryPolicy = createRetryPolicy(exceptionClass, shouldRetry, numberOfRetries, operationDescription,
      threadLocalModuleStageContext);
    retryTemplate.setRetryPolicy(retryPolicy);
    retryTemplate.setBackOffPolicy(backOffPolicy);
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
        // If there was an error that we can retry - return true
        return exceptionClass.isAssignableFrom(error.getClass()) && shouldRetry.test((T) error);
      }

      @Override
      public void registerThrowable(RetryContext context, Throwable throwable) {
        super.registerThrowable(context, throwable);
        log.error(String.format("Error occurred for %s - retrying", operationDescription), throwable);
        addErrorInformation(getStackTrace(throwable), threadLocalModuleStageContext);
      }
    };
    retryPolicy.setMaxAttempts(numberOfRetries);
    return retryPolicy;
  }
}
