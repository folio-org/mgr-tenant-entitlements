package org.folio.entitlement.controller;

import static java.lang.Boolean.TRUE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.STATE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.UPGRADE;
import static org.springframework.http.HttpStatus.CREATED;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.DesiredStateRequestBody;
import org.folio.entitlement.domain.dto.EntitlementRequestBody;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
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
    var entitlementRequest = createRequest(ENTITLE, request.getTenantId(), request.getApplications(), token,
      tenantParameters, ignoreErrors, async, false, purgeOnRollback);

    var entitlements = entitlementService.performRequest(entitlementRequest);
    return ResponseEntity.status(CREATED).body(entitlements);
  }

  @Override
  public ResponseEntity<ExtendedEntitlements> upgrade(EntitlementRequestBody request, String token,
    String tenantParameters, Boolean async) {
    var entitlementRequest = createRequest(UPGRADE, request.getTenantId(), request.getApplications(), token,
      tenantParameters, true, async, false, false);

    var entitlements = entitlementService.performRequest(entitlementRequest);
    return ResponseEntity.ok(entitlements);
  }

  @Override
  public ResponseEntity<ExtendedEntitlements> delete(EntitlementRequestBody request, String token,
    String tenantParameters, Boolean ignoreErrors, Boolean purge, Boolean async) {
    var entitlementRequest = createRequest(REVOKE, request.getTenantId(), request.getApplications(), token,
      tenantParameters, true, async, purge, false);

    var entitlements = entitlementService.performRequest(entitlementRequest);
    return ResponseEntity.ok(entitlements);
  }

  @Override
  public ResponseEntity<ExtendedEntitlements> applyState(DesiredStateRequestBody request, String token,
    String tenantParameters, Boolean ignoreErrors, Boolean async, Boolean purge, Boolean purgeOnRollback) {
    var entitlementRequest = createRequest(STATE, request.getTenantId(), request.getApplications(), token,
      tenantParameters, ignoreErrors, async, purge, purgeOnRollback);

    var entitlements = entitlementService.performRequest(entitlementRequest);
    return ResponseEntity.ok(entitlements);
  }

  private static EntitlementRequest createRequest(EntitlementRequestType type, UUID tenantId, List<String> applications,
    String token, String tenantParameters, Boolean ignoreErrors, Boolean async, Boolean purge,
    Boolean purgeOnRollback) {
    return EntitlementRequest.builder()
      .type(type)
      .okapiToken(token)
      .tenantParameters(tenantParameters)
      .tenantId(tenantId)
      .applications(applications)
      .ignoreErrors(TRUE.equals(ignoreErrors))
      .async(TRUE.equals(async))
      .purge(TRUE.equals(purge))
      .purgeOnRollback(TRUE.equals(purgeOnRollback))
      .build();
  }
}
