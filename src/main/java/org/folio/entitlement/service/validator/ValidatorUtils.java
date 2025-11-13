package org.folio.entitlement.service.validator;

import static java.lang.String.format;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.common.domain.model.error.Parameter;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.exception.RequestValidationException;
import org.semver4j.Semver;

@Log4j2
@UtilityClass
class ValidatorUtils {

  static void validateUpdatingApps(Collection<String> applicationIds, List<Entitlement> tenantEntitlements) {
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

  static Optional<Parameter> validateApplicationFlow(ApplicationFlow flow, EntitlementType type) {
    var param = new Parameter().key(flow.getApplicationId());
    if (flow.getType() != type) {
      var value = flow.getType().getValue();
      return switch (flow.getStatus()) {
        case QUEUED -> of(param.value(format("Another %s flow is in queue", value)));
        case IN_PROGRESS -> of(param.value(format("Another %s flow is in progress", value)));
        case FAILED -> of(param.value(format("Previous %s flow failed", value)));
        case CANCELLED -> of(param.value(format("Previous %s flow canceled", value)));
        case FINISHED -> checkPreviousFinishedFlow(flow, type);
        default -> empty();
      };
    }

    var typeValue = capitalize(type.getValue());
    return switch (flow.getStatus()) {
      case QUEUED -> of(param.value(typeValue + " flow is in queue"));
      case IN_PROGRESS -> of(param.value(typeValue + " flow is in progress"));
      case FINISHED -> type != UPGRADE ? of(param.value(typeValue + " flow finished")) : empty();
      default -> empty();
    };
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

  private static Optional<Parameter> checkPreviousFinishedFlow(ApplicationFlow flow, EntitlementType type) {
    if (type == ENTITLE && flow.getType() == UPGRADE) {
      var errorMessage = format("%s flow finished", capitalize(flow.getType().getValue()));
      return of(new Parameter().key(flow.getApplicationId()).value(errorMessage));
    }

    return empty();
  }
}
