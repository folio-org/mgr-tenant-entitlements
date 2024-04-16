package org.folio.entitlement.service.validator;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.utils.SemverUtils.getNames;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.integration.folio.CommonStageContext;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.entitlement.utils.SemverUtils;
import org.semver4j.Semver;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(2)
@Component
@RequiredArgsConstructor
public class UpgradeRequestValidator extends DatabaseLoggingStage<CommonStageContext>
  implements EntitlementRequestValidator {

  private final EntitlementCrudService entitlementService;

  @Override
  public void execute(CommonStageContext context) {
    var request = context.getEntitlementRequest();
    var tenantEntitlements = loadEntitlementsByApplicationNames(request);
    validateRequest(request, tenantEntitlements);

    var entitledApplicationIds = mapItems(tenantEntitlements, Entitlement::getApplicationId);
    context.withEntitledApplicationIds(entitledApplicationIds);
  }

  @Override
  public void validate(EntitlementRequest request) {
    var tenantEntitlements = loadEntitlementsByApplicationNames(request);
    validateRequest(request, tenantEntitlements);
  }

  private List<Entitlement> loadEntitlementsByApplicationNames(EntitlementRequest request) {
    var tenantId = request.getTenantId();
    var applicationIds = request.getApplications();
    var applicationNames = getNames(applicationIds);
    return entitlementService.findByApplicationNames(tenantId, applicationNames);
  }

  private static void validateRequest(EntitlementRequest request, List<Entitlement> tenantEntitlements) {
    var applicationIds = request.getApplications();
    var entitlementsByAppNames = tenantEntitlements.stream()
      .collect(toMap(entitlement -> SemverUtils.getName(entitlement.getApplicationId()), identity(), (o1, o2) -> o1));

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
    var requestVersion = Semver.parse(SemverUtils.getVersion(applicationId));
    if (entitledVersion == null) {
      return Optional.of(new Parameter().key(entitledApplicationId).value("Entitled application has invalid version"));
    }

    if (requestVersion == null) {
      return Optional.of(new Parameter().key(applicationId).value("Application has invalid version"));
    }

    return requestVersion.compareTo(entitledVersion) > 0
      ? Optional.empty()
      : Optional.of(new Parameter().key(applicationId).value("Application version is same or lower than entitled"));
  }

  @Override
  public boolean shouldValidate(EntitlementRequest entitlementRequest) {
    return entitlementRequest.getType() == UPGRADE;
  }
}
