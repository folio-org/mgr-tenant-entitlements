package org.folio.entitlement.controller;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.AppReinstallRequestBody;
import org.folio.entitlement.domain.dto.ModuleReinstallRequestBody;
import org.folio.entitlement.domain.dto.ReinstallResult;
import org.folio.entitlement.rest.resource.ReinstallApi;
import org.folio.entitlement.service.ReinstallService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnBean(ReinstallService.class)
public class ReinstallController extends BaseController implements ReinstallApi {

  private final ReinstallService reinstallService;

  @Override
  public ResponseEntity<ReinstallResult> appReinstall(AppReinstallRequestBody appReinstallRequestBody, String token,
    String tenantParameters) {
    return ResponseEntity.ofNullable(
      reinstallService.reinstallApplications(token, appReinstallRequestBody.getApplications(),
        appReinstallRequestBody.getTenantId(), tenantParameters));
  }

  @Override
  public ResponseEntity<ReinstallResult> moduleReinstall(ModuleReinstallRequestBody moduleReinstallRequestBody,
    String token, String tenantParameters) {
    return ResponseEntity.ofNullable(
      reinstallService.reinstallModules(token, moduleReinstallRequestBody.getApplicationId(),
        moduleReinstallRequestBody.getModules(), moduleReinstallRequestBody.getTenantId(), tenantParameters));
  }
}
