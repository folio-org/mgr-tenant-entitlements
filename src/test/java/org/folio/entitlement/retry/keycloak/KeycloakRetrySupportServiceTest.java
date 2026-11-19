package org.folio.entitlement.retry.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.ws.rs.WebApplicationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.folio.entitlement.configuration.RetryConfiguration;
import org.folio.entitlement.configuration.RetryConfigurationProperties;
import org.folio.entitlement.configuration.RetryConfigurationProperties.RetryBackoffConfigProps;
import org.folio.entitlement.configuration.RetryConfigurationProperties.RetryConfigProps;
import org.folio.entitlement.retry.RetryAttemptListener;
import org.folio.entitlement.service.stage.ThreadLocalModuleStageContext;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@UnitTest
@SpringJUnitConfig(KeycloakRetrySupportServiceTest.TestConfig.class)
class KeycloakRetrySupportServiceTest {

  private static final String RESULT = "result";

  @Autowired private KeycloakRetrySupportService retrySupportService;

  @Test
  void callWithRetry_positive_successfulCallIsNotRetried() {
    var calls = new AtomicInteger();

    var result = retrySupportService.callWithRetry(() -> {
      calls.incrementAndGet();
      return RESULT;
    });

    assertThat(result).isEqualTo(RESULT);
    assertThat(calls).hasValue(1);
  }

  @Test
  void callWithRetry_positive_serverErrorIsRetriedUntilSuccess() {
    var calls = new AtomicInteger();

    var result = retrySupportService.callWithRetry(() -> {
      if (calls.incrementAndGet() < 3) {
        throw new WebApplicationException("test", 500);
      }
      return RESULT;
    });

    assertThat(result).isEqualTo(RESULT);
    assertThat(calls).hasValue(3);
  }

  @Test
  void callWithRetry_negative_serverErrorIsRetriedUntilAttemptsAreExhausted() {
    var calls = new AtomicInteger();
    var error = new WebApplicationException("test", 500);

    assertThatThrownBy(() -> retrySupportService.callWithRetry(() -> {
      calls.incrementAndGet();
      throw error;
    })).isSameAs(error);

    assertThat(calls).hasValue(3);
  }

  @Test
  void callWithRetry_negative_clientErrorIsNotRetried() {
    var calls = new AtomicInteger();
    var error = new WebApplicationException("test", 400);

    assertThatThrownBy(() -> retrySupportService.callWithRetry(() -> {
      calls.incrementAndGet();
      throw error;
    })).isSameAs(error);

    assertThat(calls).hasValue(1);
  }

  @Test
  void runWithRetry_negative_serverErrorIsRetriedUntilAttemptsAreExhausted() {
    var calls = new AtomicInteger();
    var error = new WebApplicationException("test", 503);

    assertThatThrownBy(() -> retrySupportService.runWithRetry(() -> {
      calls.incrementAndGet();
      throw error;
    })).isSameAs(error);

    assertThat(calls).hasValue(3);
  }

  @Test
  void runWithRetry_negative_clientErrorIsNotRetried() {
    var calls = new AtomicInteger();
    var error = new WebApplicationException("test", 404);

    assertThatThrownBy(() -> retrySupportService.runWithRetry(() -> {
      calls.incrementAndGet();
      throw error;
    })).isSameAs(error);

    assertThat(calls).hasValue(1);
  }

  @Configuration
  @Import(RetryConfiguration.class)
  static class TestConfig {

    @Bean
    RetryConfigurationProperties retryConfigurationProperties() {
      var backoff = RetryBackoffConfigProps.builder().delay(1).maxdelay(1).multiplier(1).build();
      return RetryConfigurationProperties.builder().keycloak(RetryConfigProps.builder().backoff(backoff).build())
        .build();
    }

    @Bean
    KeycloakRetrySupportService keycloakRetrySupportService() {
      return new KeycloakRetrySupportService();
    }

    @Bean
    ThreadLocalModuleStageContext threadLocalModuleStageContext() {
      return new ThreadLocalModuleStageContext();
    }

    @Bean
    RetryAttemptListener retryAttemptListener(ThreadLocalModuleStageContext threadLocalModuleStageContext) {
      return new RetryAttemptListener(threadLocalModuleStageContext);
    }
  }
}
