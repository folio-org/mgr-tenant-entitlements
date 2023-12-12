package org.folio.entitlement.integration.keycloak;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.flow.api.StageContext;
import org.keycloak.admin.client.Keycloak;

@RequiredArgsConstructor
public class KeycloakAuthResourceCleaner extends DatabaseLoggingStage {

  private final Keycloak keycloakClient;
  private final KeycloakService keycloakService;

  @Override
  public void execute(StageContext context) {
    var request = context.<EntitlementRequest>getFlowParameter(PARAM_REQUEST);
    if (!request.isPurge()) {
      return;
    }

    var realm = context.<String>get(PARAM_TENANT_NAME);
    keycloakClient.tokenManager().grantToken();
    var applicationDescriptor = context.<ApplicationDescriptor>get(PARAM_APP_DESCRIPTOR);
    var moduleDescriptors = applicationDescriptor.getModuleDescriptors();
    emptyIfNull(moduleDescriptors).forEach(descriptor -> keycloakService.unregisterModuleResources(descriptor, realm));
  }
}
