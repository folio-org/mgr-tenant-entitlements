package org.folio.entitlement.configuration.cache;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the caches used by the entitled applications endpoint.
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "application.cache")
public class EntitlementCacheProperties {

  /**
   * Settings of the tenant name to tenant cache.
   */
  @Valid
  @NotNull
  private CacheSettings tenantId = new CacheSettings();

  /**
   * Settings of the application descriptor cache (keyed by application id).
   */
  @Valid
  @NotNull
  private CacheSettings applicationDescriptor = new CacheSettings();

  /**
   * Expiry and size settings of a single cache.
   */
  @Data
  public static class CacheSettings {

    /**
     * Time to live of an entry since it was written. Zero effectively disables the cache.
     */
    @NotNull
    @DurationMin(millis = 0)
    private Duration ttl = Duration.ofMinutes(5);

    /**
     * Maximum number of entries. Zero effectively disables the cache.
     */
    @PositiveOrZero
    private long maxSize = 100;
  }
}
