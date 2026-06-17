package org.folio.entitlement.configuration.cache;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "application.module-entitlements-cache")
public class ModuleEntitlementsCacheProperties {

  /**
   * Maximum number of cached per-module entitlement lists (Caffeine maximumSize).
   */
  private long maxSize = 1000;

  /**
   * Backstop expiry (Caffeine expireAfterWrite). Not the freshness mechanism — invalidation is
   * in-process and immediate on every write, and the warmer re-populates on a fixed delay. Keep this
   * above {@code refresh-interval} so entries are re-warmed before they expire.
   */
  private Duration ttl = Duration.ofHours(2);
}
