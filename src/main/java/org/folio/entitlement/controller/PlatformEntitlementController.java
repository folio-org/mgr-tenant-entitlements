package org.folio.entitlement.controller;

import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.UPGRADE;
import static org.springframework.http.HttpStatus.CREATED;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.dto.ExtendedEntitlements;
import org.folio.entitlement.domain.dto.ExtendedPlatformEntitlements;
import org.folio.entitlement.domain.dto.PlatformEntitlement;
import org.folio.entitlement.domain.dto.PlatformEntitlementRequestBody;
import org.folio.entitlement.domain.model.PlatformEntitlementRequest;
import org.folio.entitlement.rest.resource.PlatformEntitlementApi;
import org.folio.entitlement.service.EntitlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PlatformEntitlementController extends BaseController implements PlatformEntitlementApi {

  private final EntitlementService entitlementService;

  @Override
  public ResponseEntity<ExtendedPlatformEntitlements> createPlatformEntitlement(
    PlatformEntitlementRequestBody request, String token) {
    var result = entitlementService.performRequest(createRequest(request, ENTITLE, token));
    return ResponseEntity.status(CREATED).body(buildResponse(result));
  }

  @Override
  public ResponseEntity<ExtendedPlatformEntitlements> upgradePlatformEntitlement(
    PlatformEntitlementRequestBody request, String token) {
    var result = entitlementService.performRequest(createRequest(request, UPGRADE, token));
    return ResponseEntity.ok(buildResponse(result));
  }

  private static PlatformEntitlementRequest createRequest(PlatformEntitlementRequestBody request,
    EntitlementRequestType type, String token) {
    return PlatformEntitlementRequest.builder()
      .applicationIds(request.getApplications())
      .type(type)
      .okapiToken(token)
      .build();
  }

  private static ExtendedPlatformEntitlements buildResponse(ExtendedEntitlements result) {
    List<PlatformEntitlement> entitlements = toStream(result.getEntitlements())
      .map(entitlement -> new PlatformEntitlement().applicationId(entitlement.getApplicationId()))
      .toList();
    return new ExtendedPlatformEntitlements()
      .platformEntitlements(entitlements)
      .totalRecords(entitlements.size())
      .flowId(result.getFlowId());
  }
}
