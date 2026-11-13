package org.folio.entitlement.service.validator;

import static org.folio.common.utils.CollectionUtils.toStream;

import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.ApplicationScope;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

/**
 * Rejects platform-scoped applications requested directly in a desired-state write. Platform applications are
 * deployment-wide and are changed only through the platform endpoint.
 *
 * <p>Only the descriptors of the requested applications are checked. Platform applications reached as dependencies
 * of a requested application are not in that list, so they still validate normally.</p>
 */
@Component
@RequiredArgsConstructor
public class PlatformStateRequestValidator extends DatabaseLoggingStage<CommonStageContext> {

  @Override
  public void execute(CommonStageContext context) {
    var platformApplications = toStream(context.getApplicationDescriptors())
      .filter(descriptor -> descriptor.getScope() == ApplicationScope.PLATFORM)
      .map(descriptor -> new Parameter()
        .key(descriptor.getId())
        .value("Platform-scoped application cannot be set in a tenant desired state"))
      .toList();

    if (!platformApplications.isEmpty()) {
      throw new RequestValidationException("Desired state request contains platform-scoped applications",
        platformApplications);
    }
  }
}
