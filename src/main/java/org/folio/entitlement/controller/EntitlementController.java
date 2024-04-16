package org.folio.entitlement.controller;

import static java.lang.Boolean.TRUE;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.springframework.http.HttpStatus.CREATED;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.EntitlementRequestBody;
import org.folio.entitlement.domain.dto.Entitlements;
import org.folio.entitlement.domain.dto.ExtendedEntitlements;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.rest.resource.EntitlementApi;
import org.folio.entitlement.service.EntitlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EntitlementController extends BaseController implements EntitlementApi {

  private final EntitlementService entitlementService;

  @Override
  public ResponseEntity<Entitlements> findByQuery(String query, Boolean includeModules, Integer limit, Integer offset) {
    var entitlements = entitlementService.findByQuery(query, includeModules, limit, offset);
    return ResponseEntity.ok(new Entitlements()
      .totalRecords(entitlements.getTotalRecords())
      .entitlements(entitlements.getRecords()));
  }

  @Override
  public ResponseEntity<ExtendedEntitlements> create(EntitlementRequestBody request, String token,
    String tenantParameters, Boolean ignoreErrors, Boolean async, Boolean purgeOnRollback) {
    var entitlementRequest = EntitlementRequest.builder()
      .type(ENTITLE)
      .okapiToken(token)
      .tenantParameters(tenantParameters)
      .tenantId(request.getTenantId())
      .applications(request.getApplications())
      .ignoreErrors(TRUE.equals(ignoreErrors))
      .async(TRUE.equals(async))
      .purgeOnRollback(TRUE.equals(purgeOnRollback))
      .build();

    var entitlements = entitlementService.performRequest(entitlementRequest);
    return ResponseEntity.status(CREATED).body(entitlements);
  }

  @Override
  public ResponseEntity<ExtendedEntitlements> upgrade(EntitlementRequestBody request, String token,
    String tenantParameters, Boolean async) {
    var entitlementRequest = EntitlementRequest.builder()
      .type(UPGRADE)
      .okapiToken(token)
      .tenantParameters(tenantParameters)
      .tenantId(request.getTenantId())
      .applications(request.getApplications())
      .ignoreErrors(true)
      .async(TRUE.equals(async))
      .build();

    var entitlements = entitlementService.performRequest(entitlementRequest);
    return ResponseEntity.ok(entitlements);
  }

  @Override
  public ResponseEntity<ExtendedEntitlements> delete(EntitlementRequestBody request, String token,
    String tenantParameters, Boolean ignoreErrors, Boolean purge, Boolean async) {
    var entitlementRequest = EntitlementRequest.builder()
      .type(REVOKE)
      .async(TRUE.equals(async))
      .okapiToken(token)
      .tenantParameters(tenantParameters)
      .tenantId(request.getTenantId())
      .applications(request.getApplications())
      .ignoreErrors(true)
      .purge(TRUE.equals(purge))
      .build();

    var entitlements = entitlementService.performRequest(entitlementRequest);
    return ResponseEntity.ok(entitlements);
  }
}
