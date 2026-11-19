package org.folio.entitlement.service.stage;

import static org.folio.common.utils.CollectionUtils.toStream;

import lombok.RequiredArgsConstructor;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.folio.entitlement.service.ApplicationManagerService;
import org.springframework.stereotype.Component;

/**
 * Loads the descriptors of the entitled versions of the requested platform applications and puts them to the stage
 * context. The application flows of a platform upgrade need them to replace the dependencies of the old version.
 *
 * <p>Runs after the request validation, so every requested application is known to have an entitled version.</p>
 */
@Component
@RequiredArgsConstructor
public class PlatformEntitledDescriptorLoader extends DatabaseLoggingStage<CommonStageContext> {

  private final PlatformEntitlementRepository repository;
  private final ApplicationManagerService applicationManagerService;

  @Override
  public void execute(CommonStageContext context) {
    var request = context.getEntitlementRequest();
    var applicationNames = toStream(request.getApplications()).map(SemverUtils::getName).toList();
    var entitledApplicationIds = toStream(repository.findAllById(applicationNames))
      .map(PlatformEntitlementEntity::getApplicationId)
      .toList();
    var entitledDescriptors = applicationManagerService.getApplicationDescriptors(
      entitledApplicationIds, request.getOkapiToken());
    context.withEntitledApplicationDescriptors(entitledDescriptors);
  }
}
