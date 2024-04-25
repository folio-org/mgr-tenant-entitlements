package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;

import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.entity.ApplicationFlowEntity;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.service.EntitlementCrudService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EntitleApplicationFlowFinalizer
  extends AbstractFlowFinalizer<ApplicationFlowEntity, ApplicationStageContext> {

  private final EntitlementCrudService entitlementCrudService;

  /**
   * Injects beans from spring context.
   *
   * @param applicationFlowRepository - {@link ApplicationFlowRepository} bean
   * @param entitlementCrudService - {@link EntitlementCrudService} bean
   */
  public EntitleApplicationFlowFinalizer(ApplicationFlowRepository applicationFlowRepository,
    EntitlementCrudService entitlementCrudService) {
    super(applicationFlowRepository);
    this.entitlementCrudService = entitlementCrudService;
  }

  @Override
  @Transactional
  public void execute(ApplicationStageContext context) {
    super.execute(context);
    var entitlement = buildEntitlementFromContext(context);
    entitlementCrudService.save(entitlement);
  }

  @Override
  @Transactional
  public void cancel(ApplicationStageContext context) {
    var entitlement = buildEntitlementFromContext(context);
    entitlementCrudService.delete(entitlement);
  }

  @Override
  protected ExecutionStatus getFinalStatus() {
    return FINISHED;
  }

  private static Entitlement buildEntitlementFromContext(ApplicationStageContext context) {
    return new Entitlement().applicationId(context.getApplicationId()).tenantId(context.getTenantId());
  }
}
