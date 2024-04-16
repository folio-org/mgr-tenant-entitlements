package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;

import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.entity.ApplicationFlowEntity;
import org.folio.entitlement.integration.folio.ApplicationStageContext;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.service.EntitlementCrudService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RevokeApplicationFlowFinalizer
  extends AbstractFlowFinalizer<ApplicationFlowEntity, ApplicationStageContext> {

  private final EntitlementCrudService entitlementCrudService;

  public RevokeApplicationFlowFinalizer(ApplicationFlowRepository applicationFlowRepository,
    EntitlementCrudService entitlementCrudService) {
    super(applicationFlowRepository);
    this.entitlementCrudService = entitlementCrudService;
  }

  @Override
  @Transactional
  public void execute(ApplicationStageContext context) {
    super.execute(context);
    var applicationId = context.getApplicationId();
    var entitlement = new Entitlement().applicationId(applicationId).tenantId(context.getTenantId());
    entitlementCrudService.delete(entitlement);
  }

  @Override
  protected ExecutionStatus getFinalStatus() {
    return FINISHED;
  }
}
