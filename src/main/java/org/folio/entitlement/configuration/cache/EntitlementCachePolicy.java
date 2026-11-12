package org.folio.entitlement.configuration.cache;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.integration.token.AdminTokenProvider;
import org.springframework.stereotype.Component;

/**
 * Decides whether data loaded from mgr-tenants and mgr-applications for the entitled applications endpoint may be
 * cached and shared between callers.
 *
 * <p>Caching is allowed only when downstream calls are made under one shared identity. If the caller's own token is
 * forwarded, downstream authorization is per caller and a cache hit would skip it.</p>
 */
@Component
@RequiredArgsConstructor
public class EntitlementCachePolicy {

  private final AdminTokenProvider adminTokenProvider;

  /**
   * Tells whether the caches may be used for the current deployment.
   *
   * @return {@code true} if downstream calls are made under one shared identity, {@code false} if the caller's own
   *   token is forwarded
   */
  public boolean isEnabled() {
    return adminTokenProvider.isSharedIdentity();
  }
}
