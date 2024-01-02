package org.folio.entitlement.service.validator;

import static java.lang.String.format;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.apache.commons.lang3.StringUtils.lowerCase;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.flow.EntitlementFlowService;
import org.folio.entitlement.utils.SemverUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(1)
@Component
@RequiredArgsConstructor
public class ApplicationFlowValidator implements EntitlementRequestValidator {

  private final EntitlementFlowService entitlementFlowService;

  /**
   * Validates entitlement request.
   *
   * <p>
   * Checks that:
   *   <ul>
   *     <li>Entitled application pre-check, validates that 1 application can be entitled/revoked simultaneously</li>
   *   </ul>
   * </p>
   *
   * @param request - entitlement request
   * @throws RequestValidationException if validation issues found
   */
  @Override
  public void validate(EntitlementRequest request) {
    var applicationNames = request.getApplications().stream()
      .map(SemverUtils::getName)
      .distinct().collect(toList());
    var requestType = request.getType();
    var applicationFlows = requestType.equals(ENTITLE)
      ? entitlementFlowService.findLastFlowsByAppNames(applicationNames, request.getTenantId())
      : entitlementFlowService.findLastFlows(request.getApplications(), request.getTenantId());

    var validationErrors = applicationFlows.stream()
      .map(applicationFlow -> validateApplicationFlow(applicationFlow, requestType))
      .flatMap(Optional::stream)
      .collect(toList());

    if (CollectionUtils.isNotEmpty(validationErrors)) {
      throw new RequestValidationException("Found validation errors in entitlement request", validationErrors);
    }
  }

  @Override
  public boolean shouldValidate(EntitlementRequest entitlementRequest) {
    return true;
  }

  private static Optional<Parameter> validateApplicationFlow(ApplicationFlow flow, EntitlementType type) {
    var param = new Parameter().key(flow.getApplicationId());
    if (flow.getType() != type) {
      var value = lowerCase(flow.getType().getValue());
      return switch (flow.getStatus()) {
        case QUEUED -> of(param.value(format("Another %s flow is in queue", value)));
        case IN_PROGRESS -> of(param.value(format("Another %s flow is in progress", value)));
        case FAILED -> of(param.value(format("Previous %s flow failed", value)));
        case CANCELLED -> of(param.value(format("Previous %s flow canceled", value)));
        default -> empty();
      };
    }

    var typeValue = capitalize(lowerCase(type.getValue()));
    return switch (flow.getStatus()) {
      case QUEUED -> of(param.value(typeValue + " flow is in queue"));
      case IN_PROGRESS -> of(param.value(typeValue + " flow is in progress"));
      case FINISHED -> of(param.value(typeValue + " flow finished"));
      default -> empty();
    };
  }
}
