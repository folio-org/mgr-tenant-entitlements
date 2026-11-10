package org.folio.entitlement.service;

import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.configuration.cache.CacheConfiguration.APPLICATION_DESCRIPTOR;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.configuration.cache.EntitlementCachePolicy;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * Loads application descriptors for the entitled applications endpoint, caching them per application id.
 *
 * <p>An application descriptor is immutable while its id exists in mgr-applications, and entries are looked up by
 * application id, so no invalidation is needed when entitlements change. Only missing descriptors are requested.
 * Descriptors are returned ordered by application id.</p>
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class EntitledApplicationDescriptorLoader {

  private final ApplicationManagerService applicationManagerService;
  private final EntitlementCachePolicy cachePolicy;
  private final CacheManager cacheManager;

  /**
   * Loads application descriptors by application ids.
   *
   * <p>When the cache is enabled by {@code EntitlementCachePolicy}, cached descriptors are reused and only the missing
   * ones are requested from mgr-applications. Otherwise all descriptors are requested from mgr-applications.</p>
   *
   * @param applicationIds - application ids
   * @param authToken - authorization token used for the call to mgr-applications
   * @return application descriptors, ordered by application id when the cache is enabled
   * @throws org.folio.entitlement.exception.RequestValidationException if some applications are not found
   */
  public List<ApplicationDescriptor> load(Collection<String> applicationIds, String authToken) {
    log.debug("Loading application descriptors: applicationIds: {}", applicationIds);
    if (!cachePolicy.isEnabled()) {
      return applicationManagerService.getApplicationDescriptors(applicationIds, authToken);
    }

    var cache = getAppDescriptorCache();
    var sortedIds = toStream(applicationIds).distinct().sorted().toList();
    var descriptorsById = new HashMap<String, ApplicationDescriptor>();
    var missingIds = new ArrayList<String>();

    for (var applicationId : sortedIds) {
      var cached = cache.get(applicationId, ApplicationDescriptor.class);
      if (cached != null) {
        log.debug("Application descriptor found in cache: applicationId: {}", applicationId);
        descriptorsById.put(applicationId, cached);
      } else {
        log.debug("Application descriptor not found in cache and will be requested: applicationId: {}", applicationId);
        missingIds.add(applicationId);
      }
    }

    loadMissing(missingIds, authToken, cache, descriptorsById);
    return mapItems(sortedIds, descriptorsById::get);
  }

  private Cache getAppDescriptorCache() {
    var cache = cacheManager.getCache(APPLICATION_DESCRIPTOR);
    if (cache == null) {
      throw new IllegalStateException("Cache not found: " + APPLICATION_DESCRIPTOR);
    }

    return cache;
  }

  private void loadMissing(List<String> missingIds, String authToken, Cache cache,
    Map<String, ApplicationDescriptor> descriptorsById) {
    if (missingIds.isEmpty()) {
      return;
    }

    for (var descriptor : applicationManagerService.getApplicationDescriptors(missingIds, authToken)) {
      log.debug("Application descriptor loaded and will be cached: applicationId: {}", descriptor.getId());
      cache.put(descriptor.getId(), descriptor);
      descriptorsById.put(descriptor.getId(), descriptor);
    }
  }
}
