package org.folio.entitlement.service.validator.adp;

import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.ListUtils;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.folio.entitlement.service.ApplicationManagerService;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class ApplicationOnlyDescriptorProvider implements ApplicationDescriptorProvider {

  private final ApplicationManagerService applicationManagerService;
  private final PlatformEntitlementRepository platformEntitlementRepository;

  @Override
  public List<ApplicationDescriptor> getDescriptors(CommonStageContext context) {
    var requestDescriptors = context.getApplicationDescriptors();
    var platformEntitledAppIds = findPlatformEntitledAppIds();
    if (platformEntitledAppIds.isEmpty()) {
      return requestDescriptors;
    }

    var authToken = context.getEntitlementRequest().getOkapiToken();
    var platformDescriptors = applicationManagerService.getApplicationDescriptors(platformEntitledAppIds, authToken);
    return ListUtils.union(requestDescriptors, platformDescriptors);
  }

  @Override
  public List<ApplicationDescriptor> getDescriptors(EntitlementRequest request) {
    var applicationIds = new ArrayList<>(request.getApplications());
    if (!applicationIds.isEmpty()) {
      applicationIds.addAll(findPlatformEntitledAppIds());
    }

    return loadAppDescriptors(applicationIds, request.getTenantId(), request.getOkapiToken());
  }

  private List<String> findPlatformEntitledAppIds() {
    return platformEntitlementRepository.findAll().stream()
      .map(PlatformEntitlementEntity::getApplicationId)
      .toList();
  }

  private List<ApplicationDescriptor> loadAppDescriptors(List<String> applicationIds, UUID tenantId, String authToken) {
    if (applicationIds.isEmpty()) {
      log.debug("No application descriptors to load: tenantId = {}", tenantId);
      return emptyList();
    } else {
      log.debug("Loading application descriptors: tenantId = {}, appIds = {}", tenantId, applicationIds);

      return applicationManagerService.getApplicationDescriptors(applicationIds, authToken);
    }
  }
}
