package org.folio.entitlement.service.stage;

import static java.util.stream.Collectors.toMap;
import static org.folio.entitlement.utils.EntitlementServiceUtils.toEntitlementType;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.service.ApplicationManagerService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DesiredStateApplicationDescriptorLoader extends DatabaseLoggingStage<CommonStageContext> {

  private final ApplicationManagerService applicationManagerService;

  @Override
  public void execute(CommonStageContext context) {
    var request = context.getEntitlementRequest();
    var applicationIds = request.getApplications();
    var authToken = request.getOkapiToken();

    var descriptorsByType = context.getApplicationStateTransitionPlan().nonEmptyBuckets()
      .map(tb -> {
        var descriptors = applicationManagerService.getApplicationDescriptors(applicationIds, authToken);
        return Map.entry(toEntitlementType(tb.getTransitionType()), descriptors);
      })
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    context.withApplicationStateTransitionDescriptors(descriptorsByType);
  }
}
