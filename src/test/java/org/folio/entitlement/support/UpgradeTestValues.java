package org.folio.entitlement.support;

import static org.folio.entitlement.support.TestUtils.readCapabilityEvent;
import static org.folio.entitlement.support.TestUtils.readScheduledJobEvent;
import static org.folio.entitlement.support.TestUtils.readSystemUserEvent;
import static org.folio.entitlement.support.model.AuthorizationResource.authResource;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.entitlement.integration.kafka.model.CapabilityEventPayload;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.integration.kafka.model.ScheduledTimers;
import org.folio.entitlement.integration.kafka.model.SystemUserEvent;
import org.folio.entitlement.support.model.AuthorizationResource;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UpgradeTestValues {

  public static List<String> kongRoutesBeforeUpgrade() {
    return List.of(
      regexRouteExpression("^/folio-module1/entities/([^/]+)$", "GET"),
      exactRouteExpression("/folio-module1/entities", "POST"),
      exactRouteExpression("/folio-module2/entities", "POST"),
      exactRouteExpression("/folio-module1/entities", "GET"),
      exactRouteExpression("/folio-module1/v1/scheduled-timer", "POST"),

      regexRouteExpression("^/folio-module2/entities/([^/]+)$", "GET"),
      exactRouteExpression("/folio-module2/entities", "GET"),
      exactRouteExpression("/folio-module2/v1/scheduled-timer1", "POST"),
      exactRouteExpression("/folio-module2/v1/scheduled-timer2", "POST"),

      regexRouteExpression("^/folio-module3/entities/([^/]+)$", "GET"),
      exactRouteExpression("/folio-module3/scheduled-timer", "POST")
    );
  }

  public static List<String> kongRoutesAfterUpgrade() {
    return List.of(
      regexRouteExpression("^/folio-module1/entities/([^/]+)$", "GET"),
      exactRouteExpression("/folio-module2/entities", "PUT"),
      exactRouteExpression("/folio-module1/entities", "GET"),
      exactRouteExpression("/folio-module1/entities", "POST"),
      exactRouteExpression("/folio-module1/v1/scheduled-timer", "POST"),

      regexRouteExpression("^/folio-module2/entities/([^/]+)$", "GET"),
      exactRouteExpression("/folio-module2/v2/entities", "POST"),
      exactRouteExpression("/folio-module2/entities", "GET"),
      exactRouteExpression("/folio-module2/v2/scheduled-timer1", "POST"),
      exactRouteExpression("/folio-module2/v1/scheduled-timer2", "POST"),

      regexRouteExpression("^/folio-module4/entities/([^/]+)$", "GET"),
      exactRouteExpression("/folio-module4/scheduled-timer", "POST")
    );
  }

  public static List<AuthorizationResource> kcResourceBeforeUpgrade() {
    return List.of(
      authResource("/folio-module1/entities", "GET", "POST"),
      authResource("/folio-module1/entities/{id}", "GET"),
      authResource("/folio-module2/entities", "GET", "POST"),
      authResource("/folio-module2/entities/{id}", "GET"),
      authResource("/folio-module3/entities/{id}", "GET")
    );
  }

  public static List<AuthorizationResource> kcResourcesAfterUpgrade() {
    return List.of(
      authResource("/folio-module1/entities", "GET", "POST"),
      authResource("/folio-module1/entities/{id}", "GET"),
      authResource("/folio-module2/entities", "GET", "PUT"),
      authResource("/folio-module2/entities/{id}", "GET"),
      authResource("/folio-module2/v2/entities", "POST"),
      authResource("/folio-module4/entities/{id}", "GET")
    );
  }

  public static List<ResourceEvent<ScheduledTimers>> scheduledTimerEventsBeforeUpgrade() {
    return List.of(
      readScheduledJobEvent("json/events/folio-app6/folio-module1/timer-event.json"),
      readScheduledJobEvent("json/events/folio-app6/folio-module2/timer.json"),
      readScheduledJobEvent("json/events/folio-app6/folio-module3/timer.json")
    );
  }

  public static List<ResourceEvent<ScheduledTimers>> scheduledTimerEventsAfterUpgrade() {
    return List.of(
      readScheduledJobEvent("json/events/folio-app6/folio-module2/timer-update.json"),
      readScheduledJobEvent("json/events/folio-app6/folio-module4/timer.json"),
      readScheduledJobEvent("json/events/folio-app6/folio-module3/timer-deprecated.json")
    );
  }

  public static List<ResourceEvent<SystemUserEvent>> systemUserEventsBeforeUpgrade() {
    return List.of(
      readSystemUserEvent("json/events/folio-app6/folio-module2/system-user.json"),
      readSystemUserEvent("json/events/folio-app6/folio-module3/system-user.json")
    );
  }

  public static List<ResourceEvent<SystemUserEvent>> systemUserEventsAfterUpgrade() {
    return List.of(
      readSystemUserEvent("json/events/folio-app6/folio-module2/system-user-update.json"),
      readSystemUserEvent("json/events/folio-app6/folio-module4/system-user.json"),
      readSystemUserEvent("json/events/folio-app6/folio-module3/system-user-deprecated.json")
    );
  }

  public static List<ResourceEvent<CapabilityEventPayload>> capabilityEventsBeforeUpgrade() {
    return List.of(
      readCapabilityEvent("json/events/folio-app6/folio-module1/capability-event.json"),
      readCapabilityEvent("json/events/folio-app6/folio-module2/capability.json"),
      readCapabilityEvent("json/events/folio-app6/folio-module3/capability.json"),
      readCapabilityEvent("json/events/folio-app6/ui-module1/capability.json"),
      readCapabilityEvent("json/events/folio-app6/ui-module2/capability.json"),
      readCapabilityEvent("json/events/folio-app6/ui-module3/capability.json")
    );
  }

  public static List<ResourceEvent<CapabilityEventPayload>> capabilityEventsAfterUpgrade() {
    return List.of(
      readCapabilityEvent("json/events/folio-app6/folio-module2/capability-update.json"),
      readCapabilityEvent("json/events/folio-app6/folio-module4/capability.json"),
      readCapabilityEvent("json/events/folio-app6/ui-module2/capability-update.json"),
      readCapabilityEvent("json/events/folio-app6/ui-module4/capability.json"),
      readCapabilityEvent("json/events/folio-app6/folio-module3/capability-deprecated.json"),
      readCapabilityEvent("json/events/folio-app6/ui-module3/capability-deprecated.json")
    );
  }

  public static String exactRouteExpression(String path, String method) {
    return "(http.path == \"" + path + "\""
      + " && http.method == \"" + method + "\""
      + " && http.headers.x_okapi_tenant == \"test\")";
  }

  @SuppressWarnings("SameParameterValue")
  public static String regexRouteExpression(String expression, String method) {
    return "(http.path ~ \"" + expression + "\""
      + " && http.method == \"" + method + "\""
      + " && http.headers.x_okapi_tenant == \"test\")";
  }
}
