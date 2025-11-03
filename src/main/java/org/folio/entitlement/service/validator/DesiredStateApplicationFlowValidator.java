package org.folio.entitlement.service.validator;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.common.utils.SemverUtils.getNames;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.service.validator.ValidatorUtils.validateApplicationFlow;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.model.ApplicationStateTransitionBucket;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.flow.ApplicationFlowService;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DesiredStateApplicationFlowValidator extends DatabaseLoggingStage<CommonStageContext> {

  private final ApplicationFlowService applicationFlowService;

  @Override
  public void execute(CommonStageContext context) {
    var request = context.getEntitlementRequest();
    var transitionPlan = context.getApplicationStateTransitionPlan();

    var validationErrors = transitionPlan.nonEmptyBuckets()
      .flatMap(transitionBucket -> validateApplicationFlowsInBucket(transitionBucket, request.getTenantId()))
      .toList();

    if (isNotEmpty(validationErrors)) {
      throw new RequestValidationException("Found validation errors in desired state request", validationErrors);
    }
  }

  private Stream<Parameter> validateApplicationFlowsInBucket(ApplicationStateTransitionBucket transitionBucket,
    UUID tenantId) {
    var type = transitionBucket.getEntitlementType();
    var applicationIds = transitionBucket.getApplicationIds();

    var applicationFlows = type == REVOKE
      ? applicationFlowService.findLastFlows(applicationIds, tenantId)
      : applicationFlowService.findLastFlowsByNames(getNames(applicationIds), tenantId);

    return applicationFlows.stream()
      .map(applicationFlow -> validateApplicationFlow(applicationFlow, type))
      .flatMap(Optional::stream);
  }
}
