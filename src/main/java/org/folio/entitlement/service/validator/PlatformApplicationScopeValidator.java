package org.folio.entitlement.service.validator;

import static java.util.stream.Collectors.groupingBy;
import static org.folio.common.utils.CollectionUtils.toStream;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.ApplicationScope;
import org.folio.common.domain.model.error.Parameter;
import org.folio.common.utils.CollectionUtils;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

/**
 * Rejects the request if it names one application more than once, or if any requested application is not
 * platform-scoped. Every violation is reported, and one violation rejects the whole request.
 */
@Component
@RequiredArgsConstructor
public class PlatformApplicationScopeValidator extends DatabaseLoggingStage<CommonStageContext> {

  private final ApplicationManagerService applicationManagerService;

  @Override
  public void execute(CommonStageContext context) {
    var request = context.getEntitlementRequest();
    validateDistinctNames(request.getApplications());

    var violations = toStream(request.getApplications())
      .filter(applicationId -> !isPlatformScoped(applicationId, request.getOkapiToken()))
      .map(applicationId -> new Parameter().key(applicationId).value("Application is not platform-scoped"))
      .toList();

    if (!violations.isEmpty()) {
      throw new RequestValidationException("Application is not platform-scoped", violations);
    }
  }

  private boolean isPlatformScoped(String applicationId, String token) {
    var descriptor = applicationManagerService.getApplicationDescriptor(applicationId, token);
    return descriptor.getScope() == ApplicationScope.PLATFORM;
  }

  private static void validateDistinctNames(List<String> applicationIds) {
    var idsByName = toStream(applicationIds).collect(groupingBy(SemverUtils::getName));
    var duplicates = toStream(idsByName.values())
      .filter(ids -> ids.size() > 1)
      .flatMap(CollectionUtils::toStream)
      .map(applicationId -> new Parameter().key(applicationId).value("Application is requested more than once"))
      .toList();

    if (!duplicates.isEmpty()) {
      throw new RequestValidationException("Request contains the same application more than once", duplicates);
    }
  }
}
