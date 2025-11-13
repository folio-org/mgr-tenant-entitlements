package org.folio.entitlement.service.stage;

import static java.util.Collections.emptyList;
import static org.apache.commons.collections4.ListUtils.union;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.SemverUtils.getNames;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;

import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.ApplicationStateTransitionPlan;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.entitlement.service.EntitlementCrudService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DesiredStateApplicationDescriptorLoader extends DatabaseLoggingStage<CommonStageContext> {

  private final ApplicationManagerService applicationManagerService;
  private final EntitlementCrudService entitlementService;

  @Override
  public void execute(CommonStageContext context) {
    var request = context.getEntitlementRequest();
    var authToken = request.getOkapiToken();
    var transitionPlan = context.getApplicationStateTransitionPlan();

    var descriptorsByType = getDescriptorsByType(transitionPlan, authToken);
    context.withApplicationStateTransitionDescriptors(descriptorsByType);

    var requestedDescriptors = union(
      descriptorsByType.getOrDefault(ENTITLE, emptyList()),
      descriptorsByType.getOrDefault(UPGRADE, emptyList()));
    context.withApplicationDescriptors(requestedDescriptors);

    var upgradeBucket = transitionPlan.upgradeBucket();
    if (!upgradeBucket.isEmpty()) {
      var entitledAppIds = getEntitledApplicationIds(upgradeBucket.getApplicationIds(), request.getTenantId());
      context.withEntitledApplicationIds(entitledAppIds);

      var entitledAppDescriptors = applicationManagerService.getApplicationDescriptors(entitledAppIds, authToken);
      context.withEntitledApplicationDescriptors(entitledAppDescriptors);
    }
  }

  private List<String> getEntitledApplicationIds(Set<String> upgradeAppIds, UUID tenantId) {
    var tenantEntitlements = entitlementService.findByApplicationNames(tenantId, getNames(upgradeAppIds));
    return mapItems(tenantEntitlements, Entitlement::getApplicationId);
  }

  private EnumMap<EntitlementType, List<ApplicationDescriptor>> getDescriptorsByType(
    ApplicationStateTransitionPlan transitionPlan, String authToken) {
    var descriptorsByType = new EnumMap<EntitlementType, List<ApplicationDescriptor>>(EntitlementType.class);

    transitionPlan.nonEmptyBuckets().forEach(tb -> {
      var descriptors = applicationManagerService.getApplicationDescriptors(tb.getApplicationIds(), authToken);
      descriptorsByType.put(tb.getEntitlementType(), descriptors);
    });

    return descriptorsByType;
  }
}
