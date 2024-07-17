package org.folio.entitlement.integration.kafka;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_FLOW_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_ENTITLED_APPLICATION_ID;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_INSTALLED_MODULE_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_TYPE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.MODULE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.UI_MODULE;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.ENTITLED_APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.kafka.model.CapabilityEventPayload;
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
  @Captor private ArgumentCaptor<ResourceEvent<CapabilityEventPayload>> eventCaptor;
  @Captor private ArgumentCaptor<String> messageKeyCaptor;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @DisplayName("execute_positive_parameterized")
  @MethodSource("executeDatasetProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  void execute_positive_parameterized(@SuppressWarnings("unused") String testName, ModuleType moduleType,
    ModuleDescriptor descriptor, ModuleDescriptor installedModuleDescriptor,
    List<ResourceEvent<CapabilityEventPayload>> expectedEvents) {
    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = flowParameters(moduleType, descriptor, installedModuleDescriptor);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, contextData);

    var topicName = capabilitiesTenantTopic();
    doNothing().when(kafkaEventPublisher).send(eq(topicName), messageKeyCaptor.capture(), eventCaptor.capture());

    moduleEventPublisher.execute(stageContext);

    assertThat(eventCaptor.getAllValues()).containsExactlyElementsOf(expectedEvents);
    assertThat(messageKeyCaptor.getAllValues()).containsOnly(TENANT_ID.toString());
  }

  @Test
  void execute_positive_noEventsPublished() {
    var request = entitlementRequest();
    var descriptor = readModuleDescriptor("json/events/capabilities/module-desc-with-nothing-to-publish.json");
    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = moduleFlowParameters(request, descriptor);

    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, contextData);

    moduleEventPublisher.execute(stageContext);

    verifyNoInteractions(kafkaEventPublisher);
  }

  @Test
  void execute_positive_upgradeRequestWithNotChangedModule() {
    var flowParameters = Map.of(
      PARAM_REQUEST, EntitlementRequest.builder().tenantId(TENANT_ID).type(UPGRADE).build(),
      PARAM_MODULE_DESCRIPTOR, readModuleDescriptor("json/events/capabilities/be-module-desc.json"),
      PARAM_INSTALLED_MODULE_DESCRIPTOR, readModuleDescriptor("json/events/capabilities/be-module-desc.json"),
      PARAM_MODULE_TYPE, MODULE,
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_ENTITLED_APPLICATION_ID, ENTITLED_APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID);
    var stageContext = moduleStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, TENANT_NAME));

    var topicName = capabilitiesTenantTopic();
    doNothing().when(kafkaEventPublisher).send(eq(topicName), messageKeyCaptor.capture(), eventCaptor.capture());

    moduleEventPublisher.execute(stageContext);

    var expectedEvents = List.of(readCapabilityEvent("json/events/capabilities/unchanged-module-event.json"));
    assertThat(eventCaptor.getAllValues()).containsExactlyElementsOf(expectedEvents);
    assertThat(messageKeyCaptor.getAllValues()).containsOnly(TENANT_ID.toString());
  }

  @Test
  void getStageName_positive() {
    var request = entitlementRequest();
    var moduleDescriptor = new ModuleDescriptor().id("mod-foo-1.0.0");
    var flowParameters = moduleFlowParameters(request, moduleDescriptor);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    var result = moduleEventPublisher.getStageName(stageContext);

    assertThat(result).isEqualTo("mod-foo-1.0.0-capabilitiesModuleEventPublisher");
  }

  private static EntitlementRequest entitlementRequest() {
    return EntitlementRequest.builder()
      .type(ENTITLE)
      .tenantId(TENANT_ID)
      .build();
  }

  private static Map<String, Object> flowParameters(ModuleType moduleType, ModuleDescriptor moduleDescriptor,
    ModuleDescriptor installedModuleDescriptor) {
    var flowParameters = new HashMap<String, Object>();
    flowParameters.put(PARAM_REQUEST, entitlementRequest());
    flowParameters.put(PARAM_MODULE_TYPE, moduleType);
    flowParameters.put(PARAM_APPLICATION_ID, APPLICATION_ID);
    flowParameters.put(PARAM_ENTITLED_APPLICATION_ID, ENTITLED_APPLICATION_ID);
    flowParameters.put(PARAM_MODULE_DESCRIPTOR, moduleDescriptor);
    flowParameters.put(PARAM_INSTALLED_MODULE_DESCRIPTOR, installedModuleDescriptor);
    return flowParameters;
  }

  private static Stream<Arguments> executeDatasetProvider() {
    return Stream.of(
      arguments("valid module descriptor", MODULE,
        readModuleDescriptor("json/events/capabilities/be-module-desc.json"),
        null,
        List.of(readCapabilityEvent("json/events/capabilities/full-app-desc-event-1.json"))),

      arguments("upgrade for valid be module", MODULE,
        readModuleDescriptor("json/events/capabilities/be-module-desc-v2.json"),
        readModuleDescriptor("json/events/capabilities/be-module-desc.json"),
        List.of(readCapabilityEvent("json/events/capabilities/module-upgrade-event.json"))),

      arguments("upgrade for deprecated be module", MODULE,
        null,
        readModuleDescriptor("json/events/capabilities/be-module-desc.json"),
        List.of(readCapabilityEvent("json/events/capabilities/deprecated-module-upgrade-event.json"))),

      arguments("valid ui module descriptor", UI_MODULE,
        readModuleDescriptor("json/events/capabilities/ui-module-desc.json"),
        null,
        List.of(readCapabilityEvent("json/events/capabilities/full-app-desc-event-2.json"))),

      arguments("upgrade for valid ui module", UI_MODULE,
        readModuleDescriptor("json/events/capabilities/ui-module-desc-v2.json"),
        readModuleDescriptor("json/events/capabilities/ui-module-desc.json"),
        List.of(readCapabilityEvent("json/events/capabilities/ui-module-upgrade-event.json"))),

      arguments("upgrade for deprecated ui module", UI_MODULE,
        null,
        readModuleDescriptor("json/events/capabilities/ui-module-desc.json"),
        List.of(readCapabilityEvent("json/events/capabilities/deprecated-ui-module-upgrade-event.json"))),

      arguments("module descriptor with unmatched permissions", MODULE,
        readModuleDescriptor("json/events/capabilities/module-desc-with-unmatched-perms.json"),
        null,
        List.of(readCapabilityEvent("json/events/capabilities/desc-with-unmatched-perms-event.json")))
    );
  }
}
