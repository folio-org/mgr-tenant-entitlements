package org.folio.entitlement.integration.kafka;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.UPGRADE;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_FLOW_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_ENTITLED_APPLICATION_ID;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_INSTALLED_MODULE_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_DESCRIPTOR;
import static org.folio.entitlement.integration.kafka.model.ResourceEventType.CREATE;
import static org.folio.entitlement.integration.kafka.model.ResourceEventType.DELETE;
import static org.folio.entitlement.integration.kafka.model.ResourceEventType.UPDATE;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.ENTITLED_APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.scheduledJobsTenantCollectionTopic;
import static org.folio.entitlement.support.TestConstants.scheduledJobsTenantTopic;
import static org.folio.entitlement.support.TestValues.moduleFlowParameters;
import static org.folio.entitlement.support.TestValues.moduleStageContext;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.RoutingEntry;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.kafka.configuration.TenantEntitlementKafkaProperties;
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
class ScheduledJobModuleEventPublisherTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final String MODULE_ID_V2 = "mod-foo-2.0.0";

  @InjectMocks private ScheduledJobModuleEventPublisher moduleEventPublisher;
  @Mock private KafkaEventPublisher kafkaEventPublisher;
  @Mock private TenantEntitlementKafkaProperties tenantEntitlementKafkaProperties;

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
    var moduleDescriptor = fooModuleDescriptor();
    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(ENTITLE).build();
    var flowParameters = moduleFlowParameters(request, moduleDescriptor);
    var stageContext = moduleStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, TENANT_NAME));

    when(tenantEntitlementKafkaProperties.isProducerTenantCollection()).thenReturn(false);

    moduleEventPublisher.execute(stageContext);

    var expectedNewHandlers = asList(fooTimerRoutingEntry(), barTimerRoutingEntry());
    var fooTimerEvent = ResourceEvent.<ScheduledTimers>builder()
      .type(CREATE).tenant(TENANT_NAME).resourceName("Scheduled Job")
      .newValue(ScheduledTimers.of(MODULE_ID, APPLICATION_ID, expectedNewHandlers))
      .build();

    verify(kafkaEventPublisher).send(scheduledJobsTenantTopic(), TENANT_ID.toString(), fooTimerEvent);
  }

  @Test
  void execute_positive_useTenantCollectionTopic() {
    var moduleDescriptor = fooModuleDescriptor();
    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(ENTITLE).build();
    var flowParameters = moduleFlowParameters(request, moduleDescriptor);
    var stageContext = moduleStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, TENANT_NAME));

    when(tenantEntitlementKafkaProperties.isProducerTenantCollection()).thenReturn(true);

    moduleEventPublisher.execute(stageContext);

    var expectedNewHandlers = asList(fooTimerRoutingEntry(), barTimerRoutingEntry());
    var fooTimerEvent = ResourceEvent.<ScheduledTimers>builder()
      .type(CREATE).tenant(TENANT_NAME).resourceName("Scheduled Job")
      .newValue(ScheduledTimers.of(MODULE_ID, APPLICATION_ID, expectedNewHandlers))
      .build();

    verify(kafkaEventPublisher).send(scheduledJobsTenantCollectionTopic(), TENANT_ID.toString(), fooTimerEvent);
  }

  @Test
  void execute_positive_noTimerHandlers() {
    var moduleDescriptor = new ModuleDescriptor().id(MODULE_ID);
    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(ENTITLE).build();
    var flowParameters = moduleFlowParameters(request, moduleDescriptor);
    var stageContext = moduleStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, TENANT_NAME));

    when(tenantEntitlementKafkaProperties.isProducerTenantCollection()).thenReturn(false);

    moduleEventPublisher.execute(stageContext);

    verifyNoInteractions(kafkaEventPublisher);
  }

  @Test
  void execute_positive_upgradeRequestWithChangedModule() {
    var flowParameters = Map.of(
      PARAM_REQUEST, EntitlementRequest.builder().tenantId(TENANT_ID).type(UPGRADE).build(),
      PARAM_MODULE_DESCRIPTOR, fooModuleDescriptorV2(),
      PARAM_INSTALLED_MODULE_DESCRIPTOR, fooModuleDescriptor(),
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_ENTITLED_APPLICATION_ID, ENTITLED_APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID);
    var stageContext = moduleStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, TENANT_NAME));

    when(tenantEntitlementKafkaProperties.isProducerTenantCollection()).thenReturn(false);

    moduleEventPublisher.execute(stageContext);

    var expectedOldHandlers = asList(fooTimerRoutingEntry(), barTimerRoutingEntry());
    var fooTimerEvent = ResourceEvent.<ScheduledTimers>builder()
      .type(UPDATE).tenant(TENANT_NAME).resourceName("Scheduled Job")
      .newValue(ScheduledTimers.of(MODULE_ID_V2, APPLICATION_ID, List.of(fooTimerRoutingEntryV2())))
      .oldValue(ScheduledTimers.of(MODULE_ID, ENTITLED_APPLICATION_ID, expectedOldHandlers))
      .build();

    verify(kafkaEventPublisher).send(scheduledJobsTenantTopic(), TENANT_ID.toString(), fooTimerEvent);
  }

  @Test
  void execute_positive_upgradeRequestWithNotChangedModule() {
    var flowParameters = Map.of(
      PARAM_REQUEST, EntitlementRequest.builder().tenantId(TENANT_ID).type(UPGRADE).build(),
      PARAM_MODULE_DESCRIPTOR, fooModuleDescriptor(),
      PARAM_INSTALLED_MODULE_DESCRIPTOR, fooModuleDescriptor(),
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_ENTITLED_APPLICATION_ID, ENTITLED_APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID);
    var stageContext = moduleStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, TENANT_NAME));

    moduleEventPublisher.execute(stageContext);
    verifyNoInteractions(kafkaEventPublisher);
  }

  @Test
  void execute_positive_upgradeRequestWithDeprecatedModule() {
    var flowParameters = Map.of(
      PARAM_REQUEST, EntitlementRequest.builder().tenantId(TENANT_ID).type(UPGRADE).build(),
      PARAM_INSTALLED_MODULE_DESCRIPTOR, fooModuleDescriptor(),
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_ENTITLED_APPLICATION_ID, ENTITLED_APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID);
    var stageContext = moduleStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, TENANT_NAME));

    when(tenantEntitlementKafkaProperties.isProducerTenantCollection()).thenReturn(false);

    moduleEventPublisher.execute(stageContext);

    var expectedOldHandlers = asList(fooTimerRoutingEntry(), barTimerRoutingEntry());
    var fooTimerEvent = ResourceEvent.<ScheduledTimers>builder()
      .type(DELETE).tenant(TENANT_NAME).resourceName("Scheduled Job")
      .oldValue(ScheduledTimers.of(MODULE_ID, ENTITLED_APPLICATION_ID, expectedOldHandlers))
      .build();

    verify(kafkaEventPublisher).send(scheduledJobsTenantTopic(), TENANT_ID.toString(), fooTimerEvent);
  }

  @Test
  void getStageName_positive() {
    var request = EntitlementRequest.builder().type(ENTITLE).build();
    var moduleDescriptor = new ModuleDescriptor().id("mod-foo-1.0.0");
    var flowParameters = moduleFlowParameters(request, moduleDescriptor);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    var result = moduleEventPublisher.getStageName(stageContext);

    assertThat(result).isEqualTo("mod-foo-1.0.0-scheduledJobModuleEventPublisher");
  }

  @Test
  void getTopicNameByTenantCollection_positive() {
    var actual = moduleEventPublisher.getTopicNameByTenantCollection();

    assertThat(actual).isEqualTo("test-env.ALL.mgr-tenant-entitlements.scheduled-job");
  }

  private static ModuleDescriptor fooModuleDescriptor() {
    return new ModuleDescriptor().id(MODULE_ID)
      .addProvidesItem(new InterfaceDescriptor()
        .id("foo").version("2.0").handlers(List.of(
          new RoutingEntry().pathPattern("/foo/entities/{id}").methods(List.of("GET")),
          new RoutingEntry().pathPattern("/foo/entities").methods(List.of("POST")))))
      .addProvidesItem(new InterfaceDescriptor()
        .id("_tenant").interfaceType("system").version("2.0").handlers(List.of(
          new RoutingEntry().pathPattern("_/tenant").methods(List.of("POST")))))
      .addProvidesItem(new InterfaceDescriptor()
        .id("_timer").interfaceType("system").version("1.0").handlers(
          List.of(fooTimerRoutingEntry(), barTimerRoutingEntry())));
  }

  private static ModuleDescriptor fooModuleDescriptorV2() {
    return new ModuleDescriptor().id(MODULE_ID_V2)
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

  private static RoutingEntry barTimerRoutingEntry() {
    return new RoutingEntry().pathPattern("/bar/expire").methods(List.of("POST"));
  }
}
