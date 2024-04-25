package org.folio.entitlement.integration.kafka;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.integration.kafka.model.ModuleType.MODULE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.UI_MODULE;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.capabilitiesTenantTopic;
import static org.folio.entitlement.support.TestUtils.readCapabilityEvent;
import static org.folio.entitlement.support.TestUtils.readModuleDescriptor;
import static org.folio.entitlement.support.TestValues.moduleFlowParameters;
import static org.folio.entitlement.support.TestValues.moduleStageContext;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.kafka.model.CapabilityEventBody;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CapabilitiesModuleEventPublisherTest {

  @InjectMocks private CapabilitiesModuleEventPublisher moduleEventPublisher;
  @Mock private KafkaEventPublisher kafkaEventPublisher;
  @Captor private ArgumentCaptor<ResourceEvent<CapabilityEventBody>> eventCaptor;
  @Captor private ArgumentCaptor<String> moduleIdCaptor;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @DisplayName("execute_positive_parameterized")
  @MethodSource("executeDatasetProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  void execute_positive_parameterized(@SuppressWarnings("unused") String testName, ModuleType moduleType,
    ModuleDescriptor descriptor, List<ResourceEvent<CapabilityEventBody>> expectedEvents) {
    var request = EntitlementRequest.builder().type(ENTITLE).build();
    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = moduleFlowParameters(request, descriptor, moduleType);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, contextData);

    var topicName = capabilitiesTenantTopic();
    doNothing().when(kafkaEventPublisher).send(eq(topicName), moduleIdCaptor.capture(), eventCaptor.capture());

    moduleEventPublisher.execute(stageContext);

    assertThat(eventCaptor.getAllValues()).containsExactlyElementsOf(expectedEvents);
    assertThat(moduleIdCaptor.getAllValues()).containsExactly(descriptor.getId());
  }

  @Test
  void execute_positive_noEventsPublished() {
    var request = EntitlementRequest.builder().type(ENTITLE).build();
    var descriptor = readModuleDescriptor("json/events/capabilities/module-desc-with-nothing-to-publish.json");
    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = moduleFlowParameters(request, descriptor);

    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, contextData);

    moduleEventPublisher.execute(stageContext);

    verifyNoInteractions(kafkaEventPublisher);
  }

  @Test
  void getStageName_positive() {
    var request = EntitlementRequest.builder().type(ENTITLE).build();
    var moduleDescriptor = new ModuleDescriptor().id("mod-foo-1.0.0");
    var flowParameters = moduleFlowParameters(request, moduleDescriptor);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    var result = moduleEventPublisher.getStageName(stageContext);

    assertThat(result).isEqualTo("mod-foo-1.0.0-capabilitiesModuleEventPublisher");
  }

  private static Stream<Arguments> executeDatasetProvider() {
    return Stream.of(
      arguments("valid module descriptor", MODULE,
        readModuleDescriptor("json/events/capabilities/be-module-desc.json"),
        List.of(readCapabilityEvent("json/events/capabilities/full-app-desc-event-1.json"))),

      arguments("valid ui module descriptor", UI_MODULE,
        readModuleDescriptor("json/events/capabilities/ui-module-desc.json"),
        List.of(readCapabilityEvent("json/events/capabilities/full-app-desc-event-2.json"))),

      arguments("module descriptor with unmatched permissions", MODULE,
        readModuleDescriptor("json/events/capabilities/module-desc-with-unmatched-perms.json"),
        List.of(readCapabilityEvent("json/events/capabilities/desc-with-unmatched-perms-event.json")))
    );
  }
}
