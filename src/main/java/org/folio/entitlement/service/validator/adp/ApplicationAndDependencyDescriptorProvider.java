package org.folio.entitlement.service.validator.adp;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.join;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.stage.ApplicationDescriptorTreeLoader;
import org.springframework.stereotype.Component;

/**
 * Provides application and dependency descriptors for interface integrity validator.
 * It loads all application descriptors and their dependencies from the context
 * or from the request, depending on the method called.
 *
 * <p>In the later case, it delegates to {@code ApplicationDescriptorTreeLoader}.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class ApplicationAndDependencyDescriptorProvider implements ApplicationDescriptorProvider {

  private static final String APPLICATION_IDS = "applicationIds";

  private final ApplicationDescriptorTreeLoader applicationTreeLoader;

  @Override
  public List<ApplicationDescriptor> getDescriptors(CommonStageContext context) {
    // simply returns all application descriptors and their dependencies loaded in the context
    return context.getApplicationAndDependencyDescriptors();
  }

  @Override
  public List<ApplicationDescriptor> getDescriptors(EntitlementRequest request) {
    var applicationIds = request.getApplications();
    if (isEmpty(applicationIds)) {
      throw new RequestValidationException("No application ids provided", APPLICATION_IDS, null);
    }

    var tenantId = request.getTenantId();
    log.debug("Loading application and dependency descriptors for request: "
      + "requestType = {}, appIds = [{}], tenantId = {}",
      request::getType, () -> join(applicationIds, ", "), () -> tenantId);

    return applicationTreeLoader.load(request);
  }
}
