package org.folio.entitlement.service;

import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.PaginationUtils.subListAtOffset;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.configuration.cache.EntitlementCachePolicy;
import org.folio.entitlement.domain.dto.ApplicationDescriptors;
import org.folio.entitlement.domain.entity.EntitlementEntity;
import org.folio.entitlement.integration.token.AdminTokenProvider;
import org.folio.entitlement.repository.EntitlementRepository;
import org.springframework.stereotype.Service;

/**
 * Provides application descriptors of the applications entitled to a tenant.
 */
@Service
@RequiredArgsConstructor
public class EntitlementApplicationService {

  private final TenantIdResolver tenantIdResolver;
  private final EntitledApplicationDescriptorLoader descriptorLoader;
  private final EntitlementCachePolicy cachePolicy;
  private final EntitlementRepository entitlementRepository;
  private final AdminTokenProvider adminTokenProvider;

  /**
   * Returns a page of application descriptors of the applications entitled to the tenant.
   *
   * <p>Entitlements are always read from the database, while the tenant and the application descriptors may come from
   * caches.</p>
   *
   * @param tenant - tenant name
   * @param userToken - token of the caller
   * @param offset - number of descriptors to skip
   * @param limit - maximum number of descriptors in the page
   * @return {@link ApplicationDescriptors} with the page and the total number of entitled applications
   */
  public ApplicationDescriptors getApplicationDescriptorsByTenantName(String tenant, String userToken,
                                                                      Integer offset, Integer limit) {
    var token = adminTokenProvider.getToken(userToken);

    var applicationIds = getApplicationIds(tenant, token);
    var allDescriptors = descriptorLoader.load(applicationIds, token);

    // offset & limit must be applied after loading all applications by ids
    // otherwise we don't know the exact number of 'total records'.
    var offsetDescriptors = subListAtOffset(offset, limit, allDescriptors);

    return new ApplicationDescriptors()
      .applicationDescriptors(offsetDescriptors)
      .totalRecords(allDescriptors.size());
  }

  private List<String> getApplicationIds(String tenantName, String authToken) {
    return mapToApplicationIds(findEntitlements(tenantName, authToken));
  }

  /**
   * Entitlements are always read from the database. If none are found for a tenant id that may have come from the
   * cache, the id is re-resolved once, because the tenant could have been re-created under the same name.
   */
  private List<EntitlementEntity> findEntitlements(String tenantName, String authToken) {
    var tenantId = tenantIdResolver.resolve(tenantName, authToken).getId();
    var entitlements = entitlementRepository.findByTenantId(tenantId);
    if (!entitlements.isEmpty() || !cachePolicy.isEnabled()) {
      return entitlements;
    }

    tenantIdResolver.evict(tenantName);
    var actualTenantId = tenantIdResolver.resolve(tenantName, authToken).getId();
    return actualTenantId.equals(tenantId) ? entitlements : entitlementRepository.findByTenantId(actualTenantId);
  }

  private static List<String> mapToApplicationIds(List<EntitlementEntity> entitlements) {
    return mapItems(entitlements, EntitlementEntity::getApplicationId);
  }
}
