package org.folio.entitlement.configuration.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

@UnitTest
class EntitlementCachePropertiesTest {

  private static final String PREFIX = "application.cache";

  @Test
  void bind_positive_applicationYmlDefaults() throws IOException {
    var environment = environmentWithApplicationYml();

    var properties = bind(environment);

    assertThat(properties.getTenantId().getTtl()).isEqualTo(Duration.ofMinutes(15));
    assertThat(properties.getTenantId().getMaxSize()).isEqualTo(100);
    assertThat(properties.getApplicationDescriptor().getTtl()).isEqualTo(Duration.ofMinutes(20));
    assertThat(properties.getApplicationDescriptor().getMaxSize()).isEqualTo(300);
  }

  @Test
  void bind_positive_environmentVariablesOverrideDefaults() throws IOException {
    var environment = environmentWithApplicationYml();
    environment.getPropertySources().addFirst(new MapPropertySource("env", Map.of(
      "TENANT_ID_CACHE_TTL", "30s",
      "TENANT_ID_CACHE_MAX_SIZE", "5",
      "APP_DESCRIPTOR_CACHE_TTL", "0",
      "APP_DESCRIPTOR_CACHE_MAX_SIZE", "7")));

    var properties = bind(environment);

    assertThat(properties.getTenantId().getTtl()).isEqualTo(Duration.ofSeconds(30));
    assertThat(properties.getTenantId().getMaxSize()).isEqualTo(5);
    assertThat(properties.getApplicationDescriptor().getTtl()).isEqualTo(Duration.ZERO);
    assertThat(properties.getApplicationDescriptor().getMaxSize()).isEqualTo(7);
  }

  @Test
  void bind_positive_zeroValuesDisableCachesAndAreValid() {
    var properties = bindValidated(Map.of(
      "application.cache.tenant-id.ttl", "0",
      "application.cache.tenant-id.max-size", "0",
      "application.cache.application-descriptor.ttl", "0s",
      "application.cache.application-descriptor.max-size", "0"));

    assertThat(properties.getTenantId().getTtl()).isEqualTo(Duration.ZERO);
    assertThat(properties.getTenantId().getMaxSize()).isZero();
    assertThat(properties.getApplicationDescriptor().getTtl()).isEqualTo(Duration.ZERO);
    assertThat(properties.getApplicationDescriptor().getMaxSize()).isZero();
  }

  @Test
  void bind_negative_negativeMaxSizeIsRejected() {
    var source = Map.of("application.cache.tenant-id.max-size", "-1");

    assertThatThrownBy(() -> bindValidated(source))
      .isInstanceOf(BindException.class)
      .hasStackTraceContaining("max-size");
  }

  @Test
  void bind_negative_negativeTtlIsRejected() {
    var source = Map.of("application.cache.application-descriptor.ttl", "-1s");

    assertThatThrownBy(() -> bindValidated(source))
      .isInstanceOf(BindException.class)
      .hasStackTraceContaining("application.cache.application-descriptor.ttl");
  }

  private static EntitlementCacheProperties bindValidated(Map<String, String> source) {
    try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
      var handler = new ValidationBindHandler(new SpringValidatorAdapter(validatorFactory.getValidator()));
      var binder = new Binder(new MapConfigurationPropertySource(source));
      return binder.bind(PREFIX, Bindable.of(EntitlementCacheProperties.class), handler).get();
    }
  }

  private static StandardEnvironment environmentWithApplicationYml() throws IOException {
    var environment = new StandardEnvironment();
    var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
    sources.forEach(environment.getPropertySources()::addLast);
    return environment;
  }

  private static EntitlementCacheProperties bind(StandardEnvironment environment) {
    return Binder.get(environment).bind(PREFIX, EntitlementCacheProperties.class).get();
  }
}
