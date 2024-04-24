package org.folio.entitlement.integration.okapi.flow;

import static java.util.Arrays.asList;
import static org.folio.entitlement.utils.FlowUtils.combineStages;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceCleaner;
import org.folio.entitlement.integration.kong.KongRouteCleaner;
import org.folio.entitlement.integration.okapi.stage.OkapiModulesEventPublisher;
import org.folio.entitlement.integration.okapi.stage.OkapiModulesInstaller;
import org.folio.flow.api.Flow;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor
public class OkapiModulesRevokeFlowFactory implements OkapiModulesFlowFactory {

  private final OkapiModulesInstaller moduleInstaller;
  private final OkapiModulesEventPublisher okapiModulesEventPublisher;

  private KongRouteCleaner kongRouteCleaner;
  private KeycloakAuthResourceCleaner keycloakAuthResourceCleaner;

  @Override
  public Flow createFlow(ApplicationStageContext context) {
    var request = context.getEntitlementRequest();
    return Flow.builder()
      .id(context.flowId() + "/OkapiModulesRevokeFlow")
      .stage(combineStages("ParallelResourcesCleaner", asList(kongRouteCleaner, keycloakAuthResourceCleaner)))
      .stage(moduleInstaller)
      .stage(okapiModulesEventPublisher)
      .executionStrategy(request.getExecutionStrategy())
      .build();
  }

  @Override
  public EntitlementType getEntitlementType() {
    return EntitlementType.REVOKE;
  }

  @Autowired(required = false)
  public void setKongRouteCleaner(KongRouteCleaner kongRouteCleaner) {
    this.kongRouteCleaner = kongRouteCleaner;
  }

  @Autowired(required = false)
  public void setKeycloakAuthResourceCleaner(KeycloakAuthResourceCleaner keycloakAuthResourceCleaner) {
    this.keycloakAuthResourceCleaner = keycloakAuthResourceCleaner;
  }
}
