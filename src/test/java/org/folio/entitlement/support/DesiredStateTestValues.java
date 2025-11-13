package org.folio.entitlement.support;

import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.support.TestUtils.readCapabilityEvent;
import static org.folio.entitlement.support.TestValues.entitlementEvent;
import static org.folio.entitlement.support.base.BaseIntegrationTest.FOLIO_MODULE2_ID;
import static org.folio.entitlement.support.base.BaseIntegrationTest.FOLIO_MODULE2_V2_ID;
import static org.folio.entitlement.support.base.BaseIntegrationTest.FOLIO_MODULE3_ID;
import static org.folio.entitlement.support.base.BaseIntegrationTest.FOLIO_MODULE4_ID;
import static org.folio.entitlement.support.model.AuthorizationResource.authResource;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.entitlement.integration.kafka.model.CapabilityEventPayload;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.support.model.AuthorizationResource;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DesiredStateTestValues {

  public static final String FOLIO_MODULE8_ID = "folio-module8-8.0.0";
  public static final String FOLIO_MODULE9_ID = "folio-module9-9.0.0";

  public static List<AuthorizationResource> kcResourcesAfterComplexState() {
    return List.of(
      // upgraded
      authResource("/folio-module1/entities", "GET", "POST"),
      authResource("/folio-module1/entities/{id}", "GET"),
      authResource("/folio-module1/v1/scheduled-timer", "POST"),
      authResource("/folio-module2/entities", "GET", "PUT"),
      authResource("/folio-module2/entities/{id}", "GET"),
      authResource("/folio-module2/v1/scheduled-timer2", "POST"),
      authResource("/folio-module2/v2/entities", "POST"),
      authResource("/folio-module2/v2/scheduled-timer1", "POST"),
      authResource("/folio-module4/entities/{id}", "GET"),
      authResource("/folio-module4/scheduled-timer", "POST"),
      // installed
      authResource("/folio-module8/events", "POST"),
      authResource("/folio-module8/events/{id}", "GET")
    );
  }

  public static EntitlementEvent[] entitlementEventsAfterComplexState() {
    return new EntitlementEvent[] {
      entitlementEvent(REVOKE, FOLIO_MODULE9_ID),
      entitlementEvent(UPGRADE, FOLIO_MODULE2_V2_ID),
      entitlementEvent(UPGRADE, FOLIO_MODULE4_ID),
      entitlementEvent(REVOKE, FOLIO_MODULE2_ID),
      entitlementEvent(REVOKE, FOLIO_MODULE3_ID),
      entitlementEvent(ENTITLE, FOLIO_MODULE8_ID)
    };
  }

  public static List<ResourceEvent<CapabilityEventPayload>> capabilityEventsAfterComplexState() {
    return List.of(
      readCapabilityEvent("json/events/folio-app6/folio-module1/capability-application-id-update.json"),
      readCapabilityEvent("json/events/folio-app6/folio-module2/capability-update.json"),
      readCapabilityEvent("json/events/folio-app6/folio-module4/capability.json"),
      readCapabilityEvent("json/events/folio-app6/ui-module1/capability-application-id-update.json"),
      readCapabilityEvent("json/events/folio-app6/ui-module2/capability-update.json"),
      readCapabilityEvent("json/events/folio-app6/ui-module4/capability.json"),
      readCapabilityEvent("json/events/folio-app6/folio-module3/capability-deprecated.json"),
      readCapabilityEvent("json/events/folio-app6/ui-module3/capability-deprecated.json"),
      readCapabilityEvent("json/events/folio-app8/folio-module8/capability.json")
      );
  }
}
