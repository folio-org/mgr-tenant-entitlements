package org.folio.entitlement.service.stage;

import org.folio.entitlement.domain.entity.ApplicationFlowEntity;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commits the upgrade of one platform application, the platform counterpart of
 * {@link UpgradeApplicationFlowFinalizer}. The application name is the key of the entitlement, so saving the new
 * version replaces the entitled one in place. Cancellation restores the entitled version.
 */
@Component
public class PlatformUpgradeApplicationFlowFinalizer
  extends AbstractFlowFinalizer<ApplicationFlowEntity, ApplicationStageContext> {

  private final PlatformEntitlementRepository entitlementRepository;

  /**
   * Injects beans from spring context.
   *
   * @param applicationFlowRepository - {@link ApplicationFlowRepository} bean
   * @param statusProvider - application flow final status provider
   * @param entitlementRepository - {@link PlatformEntitlementRepository} bean
   */
  public PlatformUpgradeApplicationFlowFinalizer(ApplicationFlowRepository applicationFlowRepository,
    @Qualifier("applicationFlowFinalizerStatusProvider")
    FlowFinalizerStatusProvider<ApplicationStageContext> statusProvider,
    PlatformEntitlementRepository entitlementRepository) {
    super(applicationFlowRepository, statusProvider);
    this.entitlementRepository = entitlementRepository;
  }

  @Override
  protected void afterFlowStatusUpdate(ApplicationStageContext context) {
    var applicationId = context.getApplicationId();
    if (!applicationId.equals(context.getEntitledApplicationId())) {
      entitlementRepository.save(PlatformEntitlementEntity.of(applicationId));
    }
  }

  @Override
  @Transactional
  public void cancel(ApplicationStageContext context) {
    var entitledApplicationId = context.getEntitledApplicationId();
    if (!entitledApplicationId.equals(context.getApplicationId())) {
      entitlementRepository.save(PlatformEntitlementEntity.of(entitledApplicationId));
    }
  }
}
