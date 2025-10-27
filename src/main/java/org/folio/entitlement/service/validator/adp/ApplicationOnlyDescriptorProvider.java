package org.folio.entitlement.service.validator.adp;

import static java.util.Collections.emptyList;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.ApplicationManagerService;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class ApplicationOnlyDescriptorProvider implements ApplicationDescriptorProvider {

  private final ApplicationManagerService applicationManagerService;

  @Override
  public List<ApplicationDescriptor> getDescriptors(CommonStageContext context) {
    // simply return the application descriptors already present in the context
    return context.getApplicationDescriptors();
  }

  @Override
  public List<ApplicationDescriptor> getDescriptors(EntitlementRequest request) {
    return loadAppDescriptors(request.getApplications(), request.getTenantId(), request.getOkapiToken());
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
