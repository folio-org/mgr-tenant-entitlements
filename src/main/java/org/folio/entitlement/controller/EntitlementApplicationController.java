package org.folio.entitlement.controller;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.ApplicationDescriptors;
import org.folio.entitlement.rest.resource.EntitlementApplicationApi;
import org.folio.entitlement.service.EntitlementApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EntitlementApplicationController extends BaseController implements EntitlementApplicationApi {

  private final EntitlementApplicationService service;

  @Override
  public ResponseEntity<ApplicationDescriptors> findEntitledApplicationsByTenantName(String tenantHeader, String tenant,
    String authToken, Integer limit, Integer offset) {
    var descriptors = service.getApplicationDescriptorsByTenantName(tenant, authToken, offset, limit);
    return ResponseEntity.ok(descriptors);
  }
}
