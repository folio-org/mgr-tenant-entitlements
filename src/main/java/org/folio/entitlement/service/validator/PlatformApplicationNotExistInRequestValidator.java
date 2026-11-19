package org.folio.entitlement.service.validator;

import static org.folio.common.utils.CollectionUtils.toStream;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.common.domain.model.ApplicationScope;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlatformApplicationNotExistInRequestValidator extends DatabaseLoggingStage<CommonStageContext> {

  @Override
  public void execute(CommonStageContext context) {
    validate(context.getApplicationDescriptors());
  }

  public void validate(Collection<ApplicationDescriptor> requestedDescriptors) {
    var platformApplications = toStream(requestedDescriptors)
      .filter(descriptor -> descriptor.getScope() == ApplicationScope.PLATFORM)
      .map(descriptor -> new Parameter()
        .key(descriptor.getId())
        .value("Platform-scoped application cannot be requested in a tenant operation"))
      .toList();

    if (!platformApplications.isEmpty()) {
      throw new RequestValidationException("Tenant request contains platform-scoped applications",
        platformApplications);
    }
  }
}
