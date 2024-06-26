package org.folio.entitlement.service;

import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.PaginationUtils.subListAtOffset;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.ApplicationDescriptors;
import org.folio.entitlement.domain.entity.EntitlementEntity;
import org.folio.entitlement.integration.tm.TenantManagerService;
import org.folio.entitlement.repository.EntitlementRepository;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.AccessTokenResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntitlementApplicationService {

  private final ApplicationManagerService applicationManagerService;
  private final TenantManagerService tenantManagerService;
  private final EntitlementRepository entitlementRepository;
  private final Optional<Keycloak> keycloak;

  public ApplicationDescriptors getApplicationDescriptorsByTenantName(String tenant, String userToken,
                                                                      Integer offset, Integer limit) {
    var token = obtainAuthTokenOrElse(userToken);

    var applicationIds = getApplicationIds(tenant, token);
    var allDescriptors = applicationManagerService.getApplicationDescriptors(applicationIds, token);

    // offset & limit must be applied after loading all applications by ids
    // otherwise we don't know the exact number of 'total records'.
    var offsetDescriptors = subListAtOffset(offset, limit, allDescriptors);

    return new ApplicationDescriptors()
      .applicationDescriptors(offsetDescriptors)
      .totalRecords(allDescriptors.size());
  }

  private String obtainAuthTokenOrElse(String userToken) {
    return keycloak.map(kc -> kc.tokenManager().grantToken())
      .map(AccessTokenResponse::getToken)
      .orElse(userToken);
  }

  private List<String> getApplicationIds(String tenantName, String authToken) {
    var tenant = tenantManagerService.findTenantByName(tenantName, authToken);
    var entitlements = entitlementRepository.findByTenantId(tenant.getId());
    return mapToApplicationIds(entitlements);
  }

  private static List<String> mapToApplicationIds(List<EntitlementEntity> entitlements) {
    return mapItems(entitlements, EntitlementEntity::getApplicationId);
  }
}
