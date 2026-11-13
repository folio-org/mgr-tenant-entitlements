package org.folio.entitlement.service.stage;

import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.entity.ApplicationFlowEntity;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commits the entitlement of one platform application, the platform counterpart of
 * {@link EntitleApplicationFlowFinalizer}.
 *
 * <p>Entitling the already entitled version is a retry. It saves identical state, and a later cancellation must not
 * remove an entitlement this flow did not create, so the finalizer remembers whether it created the row.</p>
 */
@Component
public class PlatformEntitleApplicationFlowFinalizer
  extends AbstractFlowFinalizer<ApplicationFlowEntity, ApplicationStageContext> {

  private final PlatformEntitlementRepository entitlementRepository;

  /**
   * Injects beans from spring context.
   *
   * @param applicationFlowRepository - {@link ApplicationFlowRepository} bean
   * @param statusProvider - application flow final status provider
   * @param entitlementRepository - {@link PlatformEntitlementRepository} bean
   */
  public PlatformEntitleApplicationFlowFinalizer(ApplicationFlowRepository applicationFlowRepository,
    @Qualifier("applicationFlowFinalizerStatusProvider")
    FlowFinalizerStatusProvider<ApplicationStageContext> statusProvider,
    PlatformEntitlementRepository entitlementRepository) {
    super(applicationFlowRepository, statusProvider);
    this.entitlementRepository = entitlementRepository;
  }

  @Override
  protected void afterFlowStatusUpdate(ApplicationStageContext context) {
    var applicationId = context.getApplicationId();
    var alreadyEntitled = entitlementRepository.existsById(SemverUtils.getName(applicationId));

    context.withPlatformNewlyEntitled(!alreadyEntitled);

    if (!alreadyEntitled) {
      entitlementRepository.save(PlatformEntitlementEntity.of(applicationId));
    }
  }

  @Override
  public void cancel(ApplicationStageContext context) {
    if (context.isPlatformNewlyEntitled()) {
      entitlementRepository.deleteById(SemverUtils.getName(context.getApplicationId()));
    }
  }
}
