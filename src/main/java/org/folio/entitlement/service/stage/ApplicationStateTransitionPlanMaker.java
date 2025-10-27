package org.folio.entitlement.service.stage;

import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.SetUtils.difference;
import static org.apache.commons.collections4.SetUtils.intersection;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.domain.model.ApplicationStateTransitionBucket.entitle;
import static org.folio.entitlement.domain.model.ApplicationStateTransitionBucket.revoke;
import static org.folio.entitlement.domain.model.ApplicationStateTransitionBucket.upgrade;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.model.ApplicationStateTransitionPlan;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.service.EntitlementCrudService;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class ApplicationStateTransitionPlanMaker extends DatabaseLoggingStage<CommonStageContext> {

  private final EntitlementCrudService entitlementService;

  @Override
  public void execute(CommonStageContext context) {
    var request = context.getEntitlementRequest();
    var tenantId = request.getTenantId();

    var entitlements = entitlementService.findByTenantId(tenantId);
    var entitledApplicationByName = applicationNameToId(entitlements);

    var applicationByName = applicationNameToId(toStream(request.getApplications()));

    var difference = evaluateDiff(applicationByName, entitledApplicationByName);
    log.debug("Application differences evaluated for tenant: tenant = {}, difference = {}",
      context.getTenantName(), difference);

    context.withApplicationStateTransitionPlan(new ApplicationStateTransitionPlan(
      entitle(difference.toEntitle()),
      upgrade(difference.toUpgrade()),
      revoke(difference.toRevoke()))
    );
  }

  private static Map<String, String> applicationNameToId(Stream<String> applicationIds) {
    return applicationIds.collect(toMap(SemverUtils::getName, identity()));
  }

  private static Map<String, String> applicationNameToId(List<Entitlement> entitlements) {
    return applicationNameToId(toStream(entitlements).map(Entitlement::getApplicationId));
  }

  private static DifferenceResult evaluateDiff(Map<String, String> newApplicationsByName,
    Map<String, String> existingApplicationsByName) {
    var newNames = newApplicationsByName.keySet();
    var existingNames = existingApplicationsByName.keySet();

    var toEntitle = difference(newNames, existingNames).stream()
      .map(newApplicationsByName::get)
      .collect(Collectors.toSet());

    var existingAppIds = new HashSet<>(existingApplicationsByName.values());
    var toUpgrade = intersection(existingNames, newNames).stream()
      .map(newApplicationsByName::get)
      .filter(not(existingAppIds::contains)) // check if application version has changed
      .collect(Collectors.toSet());

    var toRevoke = difference(existingNames, newNames).stream()
      .map(existingApplicationsByName::get)
      .collect(Collectors.toSet());

    return new DifferenceResult(toEntitle, toUpgrade, toRevoke);
  }

  private record DifferenceResult(Set<String> toEntitle, Set<String> toUpgrade, Set<String> toRevoke) {}
}
