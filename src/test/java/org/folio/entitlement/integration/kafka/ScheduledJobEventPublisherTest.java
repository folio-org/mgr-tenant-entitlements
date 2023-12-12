package org.folio.entitlement.integration.kafka;

import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.integration.kafka.model.ResourceEventType.CREATE;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_NAME;
import static org.folio.entitlement.support.TestConstants.APPLICATION_VERSION;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.scheduledJobsTenantTopic;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.support.TestUtils;
import org.folio.flow.api.StageContext;
import org.folio.security.domain.model.descriptor.InterfaceDescriptor;
import org.folio.security.domain.model.descriptor.ModuleDescriptor;
import org.folio.security.domain.model.descriptor.RoutingEntry;
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

  @InjectMocks private ScheduledJobEventPublisher publisher;
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
    var flowParameters = Map.of(PARAM_REQUEST, request());
    var handler2 = new RoutingEntry().pathPattern("/bar/expire").methods(List.of("POST"));
    var handler1 = new RoutingEntry().pathPattern("/foo/entities/scheduled").methods(List.of("POST"));
    var applicationDescriptor = applicationDescriptor(
      moduleDescriptor(new InterfaceDescriptor().id("foo").handlers(List.of(
          new RoutingEntry().pathPattern("/foo/entities/{id}").methods(List.of("GET")),
          new RoutingEntry().pathPattern("/foo/entities").methods(List.of("POST")))),
        new InterfaceDescriptor().id("_timer").interfaceType("system").handlers(List.of(handler1))),
      moduleDescriptor(new InterfaceDescriptor().id("_timer").interfaceType("system").handlers(List.of(handler2)))
    );
    var contextParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME, PARAM_APP_DESCRIPTOR, applicationDescriptor);
    var stageContext = StageContext.of(FLOW_ID, flowParameters, contextParameters);

    publisher.execute(stageContext);

    verify(kafkaEventPublisher).send(scheduledJobsTenantTopic(), TENANT_ID.toString(), resourceEvent(handler1));
    verify(kafkaEventPublisher).send(scheduledJobsTenantTopic(), TENANT_ID.toString(), resourceEvent(handler2));
  }

  private static EntitlementRequest request() {
    return EntitlementRequest.builder().tenantId(TENANT_ID).type(ENTITLE).build();
  }

  private static ApplicationDescriptor applicationDescriptor(ModuleDescriptor... moduleDescriptors) {
    return new ApplicationDescriptor()
      .id(APPLICATION_ID)
      .name(APPLICATION_NAME)
      .version(APPLICATION_VERSION)
      .moduleDescriptors(List.of(moduleDescriptors));
  }

  private static ModuleDescriptor moduleDescriptor(InterfaceDescriptor... interfaceDescriptors) {
    return new ModuleDescriptor().provides(List.of(interfaceDescriptors));
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
