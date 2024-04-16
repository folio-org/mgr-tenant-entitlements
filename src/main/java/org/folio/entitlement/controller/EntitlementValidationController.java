package org.folio.entitlement.controller;

import static org.apache.commons.lang3.StringUtils.isBlank;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.EntitlementRequestBody;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.rest.resource.EntitlementValidationApi;
import org.folio.entitlement.service.EntitlementValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EntitlementValidationController extends BaseController implements EntitlementValidationApi {

  private final EntitlementValidationService validationService;

  @Override
  public ResponseEntity<Void> validate(EntitlementType entitlementType, EntitlementRequestBody request,
    String validator, String token) {
    var entitlementRequest = EntitlementRequest.builder()
      .type(entitlementType)
      .authToken(token)
      .tenantId(request.getTenantId())
      .applications(request.getApplications())
      .build();

    if (isBlank(validator)) {
      validationService.validate(entitlementRequest);
    } else {
      validationService.validateBy(validator, entitlementRequest);
    }

    return ResponseEntity.noContent().build();
  }
}
