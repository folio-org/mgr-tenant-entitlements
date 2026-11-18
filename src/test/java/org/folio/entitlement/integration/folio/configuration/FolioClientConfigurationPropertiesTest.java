package org.folio.entitlement.integration.folio.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

@UnitTest
class FolioClientConfigurationPropertiesTest {

  private static final String PREFIX = "application.clients.folio";

  @Test
  void getTenantJob_positive_defaults() {
    var tenantJob = new FolioClientConfigurationProperties().getTenantJob();

    assertThat(tenantJob.getTimeout()).isEqualTo(Duration.ofMinutes(30));
    assertThat(tenantJob.getWait()).isEqualTo(Duration.ofSeconds(10));
    assertThat(tenantJob.getDelay()).isEqualTo(Duration.ofSeconds(1));
    assertThat(tenantJob.getMaxDelay()).isEqualTo(Duration.ofSeconds(10));
    assertThat(tenantJob.getMultiplier()).isEqualTo(2.0);
  }

  @Test
  void validate_positive_defaultsAreValid() {
    try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
      var violations = validatorFactory.getValidator().validate(new FolioClientConfigurationProperties());

      assertThat(violations).isEmpty();
    }
  }

  @Test
  void bind_positive_boundaryValuesAreValid() {
    var properties = bindValidated(Map.of(
      PREFIX + ".tenant-job.timeout", "1ms",
      PREFIX + ".tenant-job.wait", "0",
      PREFIX + ".tenant-job.delay", "0",
      PREFIX + ".tenant-job.max-delay", "1ms",
      PREFIX + ".tenant-job.multiplier", "1"));

    var tenantJob = properties.getTenantJob();
    assertThat(tenantJob.getTimeout()).isEqualTo(Duration.ofMillis(1));
    assertThat(tenantJob.getWait()).isZero();
    assertThat(tenantJob.getDelay()).isZero();
    assertThat(tenantJob.getMaxDelay()).isEqualTo(Duration.ofMillis(1));
    assertThat(tenantJob.getMultiplier()).isEqualTo(1.0);
  }

  @ParameterizedTest
  @MethodSource("invalidTenantJobSettingsProvider")
  void bind_negative_invalidTenantJobSettingIsRejected(String key, String value) {
    var source = Map.of(PREFIX + ".tenant-job." + key, value);

    assertThatThrownBy(() -> bindValidated(source))
      .isInstanceOf(BindException.class)
      .hasStackTraceContaining(PREFIX + ".tenant-job." + key);
  }

  private static Stream<Arguments> invalidTenantJobSettingsProvider() {
    return Stream.of(
      Arguments.of("timeout", "0"),
      Arguments.of("timeout", "-1s"),
      Arguments.of("wait", "-1ms"),
      Arguments.of("delay", "-1ms"),
      Arguments.of("max-delay", "0"),
      Arguments.of("max-delay", "-1s"),
      Arguments.of("multiplier", "0.5"));
  }

  private static FolioClientConfigurationProperties bindValidated(Map<String, String> source) {
    try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
      var handler = new ValidationBindHandler(new SpringValidatorAdapter(validatorFactory.getValidator()));
      var binder = new Binder(new MapConfigurationPropertySource(source));
      return binder.bind(PREFIX, Bindable.of(FolioClientConfigurationProperties.class), handler)
        .orElseGet(FolioClientConfigurationProperties::new);
    }
  }
}
