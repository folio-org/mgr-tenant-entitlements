package org.folio.entitlement.controller;

import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.UPGRADE;
import static org.springframework.http.HttpStatus.CREATED;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.dto.ExtendedPlatformEntitlements;
import org.folio.entitlement.domain.dto.PlatformEntitlement;
import org.folio.entitlement.domain.dto.PlatformEntitlementRequestBody;
import org.folio.entitlement.domain.model.PlatformEntitlementRequest;
import org.folio.entitlement.rest.resource.PlatformEntitlementApi;
import org.folio.entitlement.service.flow.PlatformEntitlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PlatformEntitlementController extends BaseController implements PlatformEntitlementApi {

  private final PlatformEntitlementService platformEntitlementService;

  @Override
  public ResponseEntity<ExtendedPlatformEntitlements> createPlatformEntitlement(
    PlatformEntitlementRequestBody request, String token) {
    var flowId = platformEntitlementService.performRequest(createRequest(request, ENTITLE, token));
    return ResponseEntity.status(CREATED).body(buildResponse(request, flowId));
  }

  @Override
  public ResponseEntity<ExtendedPlatformEntitlements> upgradePlatformEntitlement(
    PlatformEntitlementRequestBody request, String token) {
    var flowId = platformEntitlementService.performRequest(createRequest(request, UPGRADE, token));
    return ResponseEntity.ok(buildResponse(request, flowId));
  }

  private static PlatformEntitlementRequest createRequest(PlatformEntitlementRequestBody request,
    EntitlementRequestType type, String token) {
    return PlatformEntitlementRequest.builder()
      .applicationIds(request.getApplications())
      .type(type)
      .okapiToken(token)
      .build();
  }

  private static ExtendedPlatformEntitlements buildResponse(PlatformEntitlementRequestBody request, UUID flowId) {
    List<PlatformEntitlement> entitlements = toStream(request.getApplications())
      .map(applicationId -> new PlatformEntitlement().applicationId(applicationId))
      .toList();
    return new ExtendedPlatformEntitlements()
      .platformEntitlements(entitlements)
      .totalRecords(entitlements.size())
      .flowId(flowId);
  }
}
