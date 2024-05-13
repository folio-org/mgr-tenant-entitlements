package org.folio.entitlement.integration.kafka;

import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_ENTITLED_APPLICATION_ID;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.integration.kafka.model.ResourceEventType.CREATE;
import static org.folio.entitlement.integration.kafka.model.ResourceEventType.DELETE;
import static org.folio.entitlement.integration.kafka.model.ResourceEventType.UPDATE;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_DEPRECATED_MODULE_DESCRIPTORS;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_MODULE_DESCRIPTORS;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_MODULE_DESCRIPTOR_HOLDERS;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.ENTITLED_APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.scheduledJobsTenantTopic;
import static org.folio.entitlement.support.TestValues.moduleDescriptorHolder;
import static org.folio.entitlement.support.TestValues.okapiStageContext;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.RoutingEntry;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.integration.kafka.model.ScheduledTimers;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ScheduledJobEventPublisherTest {

  private static final String FOO_MODULE_ID = "mod-foo-1.0.0";
  private static final String FOO_MODULE_V2_ID = "mod-foo-2.0.0";
  private static final String BAR_MODULE_ID = "mod-bar-1.0.0";

  @InjectMocks private ScheduledJobEventPublisher eventPublisher;
  @Mock private KafkaEventPublisher kafkaEventPublisher;

  @BeforeEach
  void setUp() {
    System.setProperty("env", "test-env");
  }

  @AfterEach
  void tearDown() {
    System.clearProperty("env");
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive() {
    var moduleDescriptors = List.of(fooModuleDescriptor(), barModuleDescriptor());
    var flowParameters = Map.of(
      PARAM_REQUEST, request(ENTITLE),
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_MODULE_DESCRIPTORS, moduleDescriptors);

    var contextParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = okapiStageContext(FLOW_ID, flowParameters, contextParameters);

    eventPublisher.execute(stageContext);

    var fooTimerEvent = ResourceEvent.<ScheduledTimers>builder()
      .type(CREATE).tenant(TENANT_NAME).resourceName("Scheduled Job")
      .newValue(ScheduledTimers.of(FOO_MODULE_ID, APPLICATION_ID, List.of(fooTimerRoutingEntry())))
      .build();
    verify(kafkaEventPublisher).send(scheduledJobsTenantTopic(), TENANT_ID.toString(), fooTimerEvent);

    var barTimerEvent = ResourceEvent.<ScheduledTimers>builder()
      .type(CREATE).tenant(TENANT_NAME).resourceName("Scheduled Job")
      .newValue(ScheduledTimers.of(BAR_MODULE_ID, APPLICATION_ID, List.of(barTimerRoutingEntry())))
      .build();
    verify(kafkaEventPublisher).send(scheduledJobsTenantTopic(), TENANT_ID.toString(), barTimerEvent);
  }

  @Test
  void execute_positive_updateRequest() {
    var flowParameters = Map.of(
      PARAM_REQUEST, request(UPGRADE),
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_ENTITLED_APPLICATION_ID, ENTITLED_APPLICATION_ID,
      PARAM_MODULE_DESCRIPTOR_HOLDERS, List.of(moduleDescriptorHolder(fooModuleDescriptorV2(), fooModuleDescriptor())),
      PARAM_DEPRECATED_MODULE_DESCRIPTORS, List.of(barModuleDescriptor()));

    var contextParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = okapiStageContext(FLOW_ID, flowParameters, contextParameters);

    eventPublisher.execute(stageContext);

    var fooTimerEvent = ResourceEvent.<ScheduledTimers>builder()
      .type(UPDATE).tenant(TENANT_NAME).resourceName("Scheduled Job")
      .newValue(ScheduledTimers.of(FOO_MODULE_V2_ID, APPLICATION_ID, List.of(fooTimerRoutingEntryV2())))
      .oldValue(ScheduledTimers.of(FOO_MODULE_ID, ENTITLED_APPLICATION_ID, List.of(fooTimerRoutingEntry())))
      .build();
    verify(kafkaEventPublisher).send(scheduledJobsTenantTopic(), TENANT_ID.toString(), fooTimerEvent);

    var barTimerEvent = ResourceEvent.<ScheduledTimers>builder()
      .type(DELETE).tenant(TENANT_NAME).resourceName("Scheduled Job")
      .oldValue(ScheduledTimers.of(BAR_MODULE_ID, ENTITLED_APPLICATION_ID, List.of(barTimerRoutingEntry())))
      .build();
    verify(kafkaEventPublisher).send(scheduledJobsTenantTopic(), TENANT_ID.toString(), barTimerEvent);
  }

  @Test
  void execute_positive_updateRequestModuleNotChanged() {
    var flowParameters = Map.of(
      PARAM_REQUEST, request(UPGRADE),
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_ENTITLED_APPLICATION_ID, ENTITLED_APPLICATION_ID,
      PARAM_MODULE_DESCRIPTOR_HOLDERS, List.of(moduleDescriptorHolder(fooModuleDescriptor(), fooModuleDescriptor())));

    var contextParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = okapiStageContext(FLOW_ID, flowParameters, contextParameters);

    eventPublisher.execute(stageContext);

    verifyNoInteractions(kafkaEventPublisher);
  }

  private static ModuleDescriptor fooModuleDescriptor() {
    return new ModuleDescriptor().id(FOO_MODULE_ID)
      .addProvidesItem(new InterfaceDescriptor()
        .id("foo").handlers(List.of(
          new RoutingEntry().pathPattern("/foo/entities/{id}").methods(List.of("GET")),
          new RoutingEntry().pathPattern("/foo/entities").methods(List.of("POST")))))
      .addProvidesItem(new InterfaceDescriptor()
        .id("_timer").interfaceType("system").handlers(List.of(fooTimerRoutingEntry())));
  }

  private static ModuleDescriptor fooModuleDescriptorV2() {
    return new ModuleDescriptor().id(FOO_MODULE_V2_ID)
      .addProvidesItem(new InterfaceDescriptor()
        .id("foo").version("1.0").handlers(List.of(
          new RoutingEntry().pathPattern("/foo/entities/{id}").methods(List.of("GET")),
          new RoutingEntry().pathPattern("/foo/entities").methods(List.of("POST")))))
      .addProvidesItem(new InterfaceDescriptor()
        .id("_timer").interfaceType("system").version("2.0").handlers(List.of(fooTimerRoutingEntryV2())));
  }

  private static RoutingEntry fooTimerRoutingEntry() {
    return new RoutingEntry().pathPattern("/foo/entities/scheduled").methods(List.of("POST"));
  }

  private static RoutingEntry fooTimerRoutingEntryV2() {
    return new RoutingEntry().pathPattern("/foo/v2/entities/scheduled").methods(List.of("POST"));
  }

  private static ModuleDescriptor barModuleDescriptor() {
    return new ModuleDescriptor().id(BAR_MODULE_ID)
      .addProvidesItem(new InterfaceDescriptor()
        .id("_timer").interfaceType("system")
        .handlers(List.of(barTimerRoutingEntry())));
  }

  private static RoutingEntry barTimerRoutingEntry() {
    return new RoutingEntry().pathPattern("/bar/expire").methods(List.of("POST"));
  }

  private static EntitlementRequest request(EntitlementType type) {
    return EntitlementRequest.builder()
      .tenantId(TENANT_ID)
      .applications(List.of(APPLICATION_ID))
      .type(type)
      .build();
  }
}
