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
@Service
@RequiredArgsConstructor
public class EntitledApplicationDescriptorLoader {

  private final ApplicationManagerService applicationManagerService;
  private final EntitlementCachePolicy cachePolicy;
  private final CacheManager cacheManager;

  public List<ApplicationDescriptor> load(Collection<String> applicationIds, String authToken) {
    if (!cachePolicy.isEnabled()) {
      return applicationManagerService.getApplicationDescriptors(applicationIds, authToken);
    }

    var cache = cacheManager.getCache(APPLICATION_DESCRIPTOR);
    var sortedIds = toStream(applicationIds).distinct().sorted().toList();
    var descriptorsById = new HashMap<String, ApplicationDescriptor>();
    var missingIds = new ArrayList<String>();

    for (var applicationId : sortedIds) {
      var cached = cache.get(applicationId, ApplicationDescriptor.class);
      if (cached != null) {
        descriptorsById.put(applicationId, cached);
      } else {
        missingIds.add(applicationId);
      }
    }

    loadMissing(missingIds, authToken, cache, descriptorsById);
    return mapItems(sortedIds, descriptorsById::get);
  }

  private void loadMissing(List<String> missingIds, String authToken, Cache cache,
    Map<String, ApplicationDescriptor> descriptorsById) {
    if (missingIds.isEmpty()) {
      return;
    }

    for (var descriptor : applicationManagerService.getApplicationDescriptors(missingIds, authToken)) {
      cache.put(descriptor.getId(), descriptor);
      descriptorsById.put(descriptor.getId(), descriptor);
    }
  }
}
