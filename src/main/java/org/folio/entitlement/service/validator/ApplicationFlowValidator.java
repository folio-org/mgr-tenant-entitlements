package org.folio.entitlement.service.validator;

import static java.lang.String.format;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.folio.common.utils.SemverUtils.getNames;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.flow.ApplicationFlowService;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(1)
@Component
@RequiredArgsConstructor
public class ApplicationFlowValidator extends DatabaseLoggingStage<CommonStageContext>
  implements EntitlementRequestValidator {

  private final ApplicationFlowService applicationFlowService;

  @Override
  public void execute(CommonStageContext context) {
    validate(context.getEntitlementRequest());
  }

  /**
   * Validates entitlement request.
   *
   * <p>
   * Checks that:
   *   <ul>
   *     <li>Entitled application pre-check, validates that only one application can be enabled, disabled,
   *     or upgraded simultaneously</li>
   *   </ul>
   * </p>
   *
   * @param request - entitlement request
   * @throws RequestValidationException if validation issues found
   */
  @Override
  public void validate(EntitlementRequest request) {
    var requestType = request.getType();
    var applicationIds = request.getApplications();
    var applicationFlows = requestType.equals(REVOKE)
      ? applicationFlowService.findLastFlows(applicationIds, request.getTenantId())
      : applicationFlowService.findLastFlowsByNames(getNames(applicationIds), request.getTenantId());

    var validationErrors = applicationFlows.stream()
      .map(applicationFlow -> validateApplicationFlow(applicationFlow, requestType))
      .flatMap(Optional::stream)
      .toList();

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
      var value = flow.getType().getValue();
      return switch (flow.getStatus()) {
        case QUEUED -> of(param.value(format("Another %s flow is in queue", value)));
        case IN_PROGRESS -> of(param.value(format("Another %s flow is in progress", value)));
        case FAILED -> of(param.value(format("Previous %s flow failed", value)));
        case CANCELLED -> of(param.value(format("Previous %s flow canceled", value)));
        default -> empty();
      };
    }

    var typeValue = capitalize(type.getValue());
    return switch (flow.getStatus()) {
      case QUEUED -> of(param.value(typeValue + " flow is in queue"));
      case IN_PROGRESS -> of(param.value(typeValue + " flow is in progress"));
      case FINISHED -> type != UPGRADE ? of(param.value(typeValue + " flow finished")) : Optional.empty();
      default -> empty();
    };
  }
}
