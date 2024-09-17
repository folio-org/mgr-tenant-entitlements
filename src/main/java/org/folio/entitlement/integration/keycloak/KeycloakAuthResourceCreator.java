package org.folio.entitlement.integration.keycloak;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.folio.common.utils.CollectionUtils.reverseList;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.okapi.model.OkapiStageContext;
import org.folio.entitlement.retry.KeycloakCallsRetryable;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.keycloak.admin.client.Keycloak;

@Log4j2
@RequiredArgsConstructor
@KeycloakCallsRetryable
public class KeycloakAuthResourceCreator extends DatabaseLoggingStage<OkapiStageContext> {

  private final Keycloak keycloakClient;
  private final KeycloakService keycloakService;

  @Override
  public void execute(OkapiStageContext ctx) {
    var realm = ctx.getTenantName();
    var moduleDescriptors = ctx.getModuleDescriptors();
    keycloakClient.tokenManager().grantToken();
    emptyIfNull(moduleDescriptors).forEach(desc -> keycloakService.updateAuthResources(null, desc, realm));
  }

  @Override
  public void cancel(OkapiStageContext ctx) {
    var request = ctx.getEntitlementRequest();
    if (!request.isPurgeOnRollback()) {
      log.debug("Skipping purge of Keycloak resource during rollback: applicationId = {}",  ctx.getApplicationId());
      return;
    }

    var realm = ctx.getTenantName();
    keycloakClient.tokenManager().grantToken();
    var moduleDescriptors = reverseList(ctx.getModuleDescriptors());
    emptyIfNull(moduleDescriptors).forEach(descriptor -> keycloakService.removeAuthResources(descriptor, realm));
  }

  @Override
  public boolean shouldCancelIfFailed(OkapiStageContext context) {
    return true;
  }
}
