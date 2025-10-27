package org.folio.entitlement.service.validator;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.folio.common.utils.SemverUtils.getNames;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.common.domain.model.error.Parameter;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.semver4j.Semver;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DesiredStateWithUpgradeValidator extends DatabaseLoggingStage<CommonStageContext> {

  private final EntitlementCrudService entitlementService;

  @Override
  public void execute(CommonStageContext context) {
    var upgradeBucket = context.getApplicationStateTransitionPlan().upgradeBucket();
    if (upgradeBucket.isEmpty()) {
      return;
    }

    var tenantId = context.getEntitlementRequest().getTenantId();
    var applicationIds = upgradeBucket.getApplicationIds();

    var tenantEntitlements = loadEntitlementsByApplicationNames(tenantId, applicationIds);
    validateRequest(applicationIds, tenantEntitlements);

    // TODO (Dima Tkachenko): review code
    /*var entitledApplicationIds = mapItems(tenantEntitlements, Entitlement::getApplicationId);
    context.withEntitledApplicationIds(entitledApplicationIds);*/
  }

  private List<Entitlement> loadEntitlementsByApplicationNames(UUID tenantId, Collection<String> applicationIds) {
    List<String> applicationNames;
    try {
      applicationNames = getNames(applicationIds);
    } catch (IllegalArgumentException e) {
      throw new RequestValidationException("Invalid applications provided for upgrade", "details", e.getMessage());
    }

    return entitlementService.findByApplicationNames(tenantId, applicationNames);
  }

  private static void validateRequest(Collection<String> applicationIds, List<Entitlement> tenantEntitlements) {
    var entitlementsByAppNames = tenantEntitlements.stream()
      .collect(toMap(entitlement -> {
        try {
          return SemverUtils.getName(entitlement.getApplicationId());
        } catch (Exception e) {
          throw new RequestValidationException("Invalid applications provided for upgrade", "details", e.getMessage());
        }
      }, identity(), (o1, o2) -> o1));

    var validationErrors = applicationIds.stream()
      .map(applicationId -> validateApplicationId(applicationId, entitlementsByAppNames))
      .flatMap(Optional::stream)
      .toList();

    if (CollectionUtils.isNotEmpty(validationErrors)) {
      throw new RequestValidationException("Invalid applications provided for upgrade", validationErrors);
    }
  }

  private static Optional<Parameter> validateApplicationId(String applicationId, Map<String, Entitlement> appNames) {
    var applicationName = SemverUtils.getName(applicationId);
    var entitlementByName = appNames.get(applicationName);
    if (entitlementByName == null) {
      return Optional.of(new Parameter().key(applicationId).value("Entitlement is not found for application"));
    }

    var entitledApplicationId = entitlementByName.getApplicationId();
    var entitledVersion = Semver.parse(SemverUtils.getVersion(entitledApplicationId));
    if (entitledVersion == null) {
      return Optional.of(new Parameter().key(entitledApplicationId).value("Entitled application has invalid version"));
    }

    var requestVersion = Semver.parse(SemverUtils.getVersion(applicationId));
    if (requestVersion == null) {
      return Optional.of(new Parameter().key(applicationId).value("Application has invalid version"));
    }

    return requestVersion.compareTo(entitledVersion) > 0
      ? Optional.empty()
      : Optional.of(new Parameter().key(applicationId).value("Application version is same or lower than entitled"));
  }
}
