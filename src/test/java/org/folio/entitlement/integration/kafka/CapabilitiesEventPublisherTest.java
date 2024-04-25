package org.folio.entitlement.integration.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.capabilitiesTenantTopic;
import static org.folio.entitlement.support.TestUtils.readApplicationDescriptor;
import static org.folio.entitlement.support.TestUtils.readCapabilityEvent;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.flowParameters;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.collections4.ListUtils;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.kafka.model.CapabilityEventBody;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CapabilitiesEventPublisherTest {

  private CapabilitiesEventPublisher capabilitiesEventPublisher;
  @Mock private KafkaEventPublisher kafkaEventPublisher;
  @Captor private ArgumentCaptor<ResourceEvent<CapabilityEventBody>> eventCaptor;
  @Captor private ArgumentCaptor<String> moduleIdCaptor;

  @BeforeEach
  void setUp() {
    var capabilitiesModuleEventPublisher = new CapabilitiesModuleEventPublisher(kafkaEventPublisher);
    this.capabilitiesEventPublisher = new CapabilitiesEventPublisher(capabilitiesModuleEventPublisher);
  }

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @DisplayName("execute_positive_parameterized")
  @MethodSource("executeDatasetProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  void execute_positive_parameterized(@SuppressWarnings("unused") String testName,
    ApplicationDescriptor descriptor, List<ResourceEvent<CapabilityEventBody>> expectedEvents) {
    var request = EntitlementRequest.builder().type(ENTITLE).build();
    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = flowParameters(request, descriptor);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, contextData);

    var topicName = capabilitiesTenantTopic();
    doNothing().when(kafkaEventPublisher).send(eq(topicName), moduleIdCaptor.capture(), eventCaptor.capture());

    capabilitiesEventPublisher.execute(stageContext);

    assertThat(eventCaptor.getAllValues()).containsExactlyElementsOf(expectedEvents);

    var expectedModuleIds = mapItems(descriptor.getModuleDescriptors(), ModuleDescriptor::getId);
    var expectedUiModuleIds = mapItems(descriptor.getUiModuleDescriptors(), ModuleDescriptor::getId);
    var expectedModuleIdentifier = ListUtils.union(expectedModuleIds, expectedUiModuleIds);
    assertThat(moduleIdCaptor.getAllValues()).containsExactlyElementsOf(expectedModuleIdentifier);
  }

  @Test
  void execute_positive_noEventsPublished() {
    var request = EntitlementRequest.builder().type(ENTITLE).build();
    var descriptor = readApplicationDescriptor("json/events/capabilities/desc-with-nothing-to-publish.json");
    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = flowParameters(request, descriptor);

    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, contextData);

    capabilitiesEventPublisher.execute(stageContext);

    verifyNoInteractions(kafkaEventPublisher);
  }

  private static Stream<Arguments> executeDatasetProvider() {
    return Stream.of(
      arguments("valid application descriptor",
        readApplicationDescriptor("json/events/capabilities/full-app-desc.json"),
        List.of(
          readCapabilityEvent("json/events/capabilities/full-app-desc-event-1.json"),
          readCapabilityEvent("json/events/capabilities/full-app-desc-event-2.json"),
          readCapabilityEvent("json/events/capabilities/full-app-desc-event-3.json"))),

      arguments("application descriptor with unmatched permissions",
        readApplicationDescriptor("json/events/capabilities/desc-with-unmatched-perms.json"),
        List.of(readCapabilityEvent("json/events/capabilities/desc-with-unmatched-perms-event.json"))),

      arguments("application descriptor with plain permissions",
        readApplicationDescriptor("json/events/capabilities/desc-with-plain-capabilities.json"),
        List.of(
          readCapabilityEvent("json/events/capabilities/desc-with-plain-capabilities-event-1.json"),
          readCapabilityEvent("json/events/capabilities/desc-with-plain-capabilities-event-2.json"))
      )
    );
  }
}
