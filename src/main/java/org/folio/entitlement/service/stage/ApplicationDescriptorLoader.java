package org.folio.entitlement.service.stage;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.service.ApplicationManagerService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApplicationDescriptorLoader extends DatabaseLoggingStage<CommonStageContext> {

  private final ApplicationManagerService applicationManagerService;

  @Override
  public void execute(CommonStageContext context) {
    var request = context.getEntitlementRequest();
    var applicationIds = request.getApplications();
    var authToken = request.getOkapiToken();
    var descriptors = applicationManagerService.getApplicationDescriptors(applicationIds, authToken);
    context.withApplicationDescriptors(descriptors);

    var entitledApplicationIds = context.getEntitledApplicationIds();
    if (CollectionUtils.isNotEmpty(entitledApplicationIds)) {
      var entitledDescriptors = applicationManagerService.getApplicationDescriptors(entitledApplicationIds, authToken);
      context.withEntitledApplicationDescriptors(entitledDescriptors);
    }
  }
}
