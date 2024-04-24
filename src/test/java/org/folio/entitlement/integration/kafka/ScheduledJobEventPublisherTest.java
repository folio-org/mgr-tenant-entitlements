package org.folio.entitlement.integration.kafka;

import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_DESCRIPTOR;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.integration.kafka.model.ResourceEventType.CREATE;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_NAME;
import static org.folio.entitlement.support.TestConstants.APPLICATION_VERSION;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.scheduledJobsTenantTopic;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.RoutingEntry;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ScheduledJobEventPublisherTest {

  private ScheduledJobEventPublisher publisher;
  @Mock private KafkaEventPublisher kafkaEventPublisher;

  @BeforeEach
  void setUp() {
    System.setProperty("env", "test-env");
    var scheduledJobModuleEventPublisher = new ScheduledJobModuleEventPublisher(kafkaEventPublisher);
    this.publisher = new ScheduledJobEventPublisher(scheduledJobModuleEventPublisher);
  }

  @AfterEach
  void tearDown() {
    System.clearProperty("env");
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive() {
    var applicationDescriptor = applicationDescriptor(fooModuleDescriptor(), barModuleDescriptor());
    var flowParameters = Map.of(PARAM_REQUEST, request(ENTITLE), PARAM_APPLICATION_DESCRIPTOR, applicationDescriptor);
    var contextParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = appStageContext(FLOW_ID, flowParameters, contextParameters);

    publisher.execute(stageContext);

    var fooResourceEvent = resourceEvent(fooTimerRoutingEntry());
    var barResourceEvent = resourceEvent(barTimerRoutingEntry());
    verify(kafkaEventPublisher).send(scheduledJobsTenantTopic(), TENANT_ID.toString(), fooResourceEvent);
    verify(kafkaEventPublisher).send(scheduledJobsTenantTopic(), TENANT_ID.toString(), barResourceEvent);
  }

  private static ModuleDescriptor fooModuleDescriptor() {
    return new ModuleDescriptor().id("mod-foo-1.0.0")
      .addProvidesItem(new InterfaceDescriptor()
        .id("foo").handlers(List.of(
          new RoutingEntry().pathPattern("/foo/entities/{id}").methods(List.of("GET")),
          new RoutingEntry().pathPattern("/foo/entities").methods(List.of("POST")))))
      .addProvidesItem(new InterfaceDescriptor()
        .id("_timer").interfaceType("system").handlers(List.of(fooTimerRoutingEntry())));
  }

  private static RoutingEntry fooTimerRoutingEntry() {
    return new RoutingEntry().pathPattern("/foo/entities/scheduled").methods(List.of("POST"));
  }

  private static ModuleDescriptor barModuleDescriptor() {
    return new ModuleDescriptor().id("mod-foo-1.0.0")
      .addProvidesItem(new InterfaceDescriptor()
        .id("_timer").interfaceType("system")
        .handlers(List.of(barTimerRoutingEntry())));
  }

  private static RoutingEntry barTimerRoutingEntry() {
    return new RoutingEntry().pathPattern("/bar/expire").methods(List.of("POST"));
  }

  private static EntitlementRequest request(EntitlementType type) {
    return EntitlementRequest.builder().tenantId(TENANT_ID).type(type).build();
  }

  private static ApplicationDescriptor applicationDescriptor(ModuleDescriptor... moduleDescriptors) {
    return new ApplicationDescriptor()
      .id(APPLICATION_ID)
      .name(APPLICATION_NAME)
      .version(APPLICATION_VERSION)
      .moduleDescriptors(List.of(moduleDescriptors));
  }

  private static ResourceEvent<RoutingEntry> resourceEvent(RoutingEntry entry) {
    return ResourceEvent.<RoutingEntry>builder()
      .type(CREATE)
      .tenant(TENANT_NAME)
      .resourceName("Scheduled Job")
      .newValue(entry)
      .build();
  }
}
