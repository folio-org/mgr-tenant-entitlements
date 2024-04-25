package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;

import java.util.UUID;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.entity.ApplicationFlowEntity;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.service.EntitlementCrudService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UpgradeApplicationFlowFinalizer
  extends AbstractFlowFinalizer<ApplicationFlowEntity, ApplicationStageContext>  {

  private final EntitlementCrudService entitlementCrudService;

  /**
   * Injects beans from spring context.
   *
   * @param applicationFlowRepository - {@link ApplicationFlowRepository} bean
   * @param entitlementCrudService - {@link EntitlementCrudService} bean
   */
  public UpgradeApplicationFlowFinalizer(ApplicationFlowRepository applicationFlowRepository,
    EntitlementCrudService entitlementCrudService) {
    super(applicationFlowRepository);
    this.entitlementCrudService = entitlementCrudService;
  }

  @Override
  @Transactional
  public void execute(ApplicationStageContext context) {
    super.execute(context);
    var tenantId = context.getTenantId();
    entitlementCrudService.delete(buildEntitlement(tenantId, context.getEntitledApplicationId()));
    entitlementCrudService.save(buildEntitlement(tenantId, context.getApplicationId()));
  }

  @Override
  protected ExecutionStatus getFinalStatus() {
    return FINISHED;
  }

  private static Entitlement buildEntitlement(UUID tenantId, String applicationId) {
    return new Entitlement().applicationId(applicationId).tenantId(tenantId);
  }
}
