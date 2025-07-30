package org.folio.entitlement.service.validator.adp;

import static java.util.Collections.emptyList;
import static org.folio.common.utils.SemverUtils.getName;
import static org.folio.common.utils.SemverUtils.getNames;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.ListUtils;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.entitlement.service.EntitlementCrudService;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class ApplicationAndEntitledDescriptorProvider implements ApplicationDescriptorProvider {

  private final ApplicationManagerService applicationManagerService;
  private final EntitlementCrudService entitlementService;

  @Override
  public List<ApplicationDescriptor> getDescriptors(CommonStageContext context) {
    var request = context.getEntitlementRequest();

    var entitledAppIdsToLoad = getEntitledAppIdsToLoad(request);
    var entitledAppDescriptors = loadAppDescriptors(entitledAppIdsToLoad, request.getTenantId(),
      request.getOkapiToken());

    var requestAppDescriptors = context.getApplicationDescriptors();

    return ListUtils.union(requestAppDescriptors, entitledAppDescriptors);
  }

  @Override
  public List<ApplicationDescriptor> getDescriptors(EntitlementRequest request) {
    var entitledAppIdsToLoad = getEntitledAppIdsToLoad(request);
    var requestAndEntitledAppIds = ListUtils.union(request.getApplications(), entitledAppIdsToLoad);

    return loadAppDescriptors(requestAndEntitledAppIds, request.getTenantId(), request.getOkapiToken());
  }

  private List<String> getEntitledAppIdsToLoad(EntitlementRequest request) {
    log.debug("Calculating entitled application ids to load for request: tenantId = {}, requestAppIds = {}",
      request.getTenantId(), request.getApplications());

    var requestAppIds = request.getApplications();
    var excludedAppNames = Set.copyOf(getNames(requestAppIds));

    var entitlements = entitlementService.findByTenantId(request.getTenantId());

    return entitlements.stream()
      .map(Entitlement::getApplicationId)
      .filter(notExcluded(excludedAppNames))
      .toList();
  }

  private List<ApplicationDescriptor> loadAppDescriptors(List<String> applicationIds, UUID tenantId,
    String authToken) {
    if (applicationIds.isEmpty()) {
      log.debug("No application descriptors to load: tenantId = {}", tenantId);
      return emptyList();
    } else {
      log.debug("Loading application descriptors: tenantId = {}, appIds = {}", tenantId, applicationIds);

      return applicationManagerService.getApplicationDescriptors(applicationIds, authToken);
    }
  }

  private static Predicate<String> notExcluded(Set<String> excludedAppNames) {
    return entitledId -> !excludedAppNames.contains(getName(entitledId));
  }
}
