package org.folio.entitlement.controller;

import static java.lang.Boolean.TRUE;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementType.STATE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.springframework.http.HttpStatus.CREATED;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.EntitlementRequestBody;
import org.folio.entitlement.domain.dto.EntitlementType;
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
  public ResponseEntity<Entitlements> findByQueryOrTenantName(String query, String tenant, Boolean includeModules,
    Integer limit, Integer offset, String token) {
    var entitlements = entitlementService.findByQueryOrTenantName(query, tenant, includeModules, limit, offset, token);
    return ResponseEntity.ok(new Entitlements()
      .totalRecords(entitlements.getTotalRecords())
      .entitlements(entitlements.getRecords()));
  }

  @Override
  public ResponseEntity<ExtendedEntitlements> create(EntitlementRequestBody request, String token,
    String tenantParameters, Boolean ignoreErrors, Boolean async, Boolean purgeOnRollback) {
    var entitlementRequest = createRequest(ENTITLE, request, token, tenantParameters, ignoreErrors, async,
      purgeOnRollback);

    var entitlements = entitlementService.performRequest(entitlementRequest);
    return ResponseEntity.status(CREATED).body(entitlements);
  }

  @Override
  public ResponseEntity<ExtendedEntitlements> upgrade(EntitlementRequestBody request, String token,
    String tenantParameters, Boolean async) {
    var entitlementRequest = createRequest(UPGRADE, request, token, tenantParameters, true, async, false);

    var entitlements = entitlementService.performRequest(entitlementRequest);
    return ResponseEntity.ok(entitlements);
  }

  @Override
  public ResponseEntity<ExtendedEntitlements> delete(EntitlementRequestBody request, String token,
    String tenantParameters, Boolean ignoreErrors, Boolean purge, Boolean async) {
    var entitlementRequest = createRequest(REVOKE, request, token, tenantParameters, true, async, purge);

    var entitlements = entitlementService.performRequest(entitlementRequest);
    return ResponseEntity.ok(entitlements);
  }

  @Override
  public ResponseEntity<ExtendedEntitlements> applyState(EntitlementRequestBody request, String token,
    String tenantParameters, Boolean ignoreErrors, Boolean async, Boolean purgeOnRollback) {
    var entitlementRequest = createRequest(STATE, request, token, tenantParameters, ignoreErrors, async,
      purgeOnRollback);

    var entitlements = entitlementService.performRequest(entitlementRequest);
    return ResponseEntity.ok(entitlements);
  }

  private static EntitlementRequest createRequest(EntitlementType type, EntitlementRequestBody request, String token,
    String tenantParameters, Boolean ignoreErrors, Boolean async, Boolean purgeOnRollback) {
    return EntitlementRequest.builder()
      .type(type)
      .okapiToken(token)
      .tenantParameters(tenantParameters)
      .tenantId(request.getTenantId())
      .applications(request.getApplications())
      .ignoreErrors(TRUE.equals(ignoreErrors))
      .async(TRUE.equals(async))
      .purgeOnRollback(TRUE.equals(purgeOnRollback))
      .build();
  }
}
