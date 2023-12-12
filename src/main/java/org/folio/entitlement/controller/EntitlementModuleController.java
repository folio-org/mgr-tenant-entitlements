package org.folio.entitlement.controller;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.Entitlements;
import org.folio.entitlement.rest.resource.EntitlementModuleApi;
import org.folio.entitlement.service.EntitlementModuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EntitlementModuleController implements EntitlementModuleApi {

  private final EntitlementModuleService service;

  @Override
  public ResponseEntity<Entitlements> getModuleEntitlements(String moduleId, Integer limit, Integer offset) {
    var entitlements = service.getModuleEntitlements(moduleId, limit, offset);
    return ResponseEntity.ok(entitlements);
  }
}
