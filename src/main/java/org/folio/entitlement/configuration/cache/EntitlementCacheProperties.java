package org.folio.entitlement.configuration.cache;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Settings of the caches used by the entitled applications endpoint.
 */
@Data
@Component
@ConfigurationProperties(prefix = "application.cache")
public class EntitlementCacheProperties {

  /**
   * Settings of the tenant name to tenant cache.
   */
  private CacheSettings tenantId = new CacheSettings();

  /**
   * Settings of the application descriptor cache (keyed by application id).
   */
  private CacheSettings applicationDescriptor = new CacheSettings();

  /**
   * Expiry and size settings of a single cache.
   */
  @Data
  public static class CacheSettings {

    /**
     * Time to live of an entry since it was written. Zero effectively disables the cache.
     */
    private Duration ttl = Duration.ofMinutes(5);

    /**
     * Maximum number of entries. Zero effectively disables the cache.
     */
    private long maxSize = 100;
  }
}
