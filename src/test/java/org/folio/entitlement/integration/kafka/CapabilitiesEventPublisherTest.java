package org.folio.entitlement.integration.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_ENTITLED_APPLICATION_ID;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_DEPRECATED_MODULE_DESCRIPTORS;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_DEPRECATED_UI_MODULE_DESCRIPTORS;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_MODULE_DESCRIPTORS;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_MODULE_DESCRIPTOR_HOLDERS;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_UI_MODULE_DESCRIPTORS;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_UI_MODULE_DESCRIPTOR_HOLDERS;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.ENTITLED_APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.capabilitiesTenantTopic;
import static org.folio.entitlement.support.TestUtils.readApplicationDescriptor;
import static org.folio.entitlement.support.TestUtils.readCapabilityEvent;
import static org.folio.entitlement.support.TestUtils.readModuleDescriptor;
import static org.folio.entitlement.support.TestValues.moduleDescriptorHolder;
import static org.folio.entitlement.support.TestValues.okapiStageContext;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.kafka.model.CapabilityEventPayload;
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
class CapabilitiesEventPublisherTest {

  private static final String TOPIC_NAME = capabilitiesTenantTopic();

  @InjectMocks private CapabilitiesEventPublisher eventPublisher;
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
  void execute_positive_parameterized(@SuppressWarnings("unused") String testName,
    ApplicationDescriptor descriptor, List<ResourceEvent<CapabilityEventPayload>> expectedEvents) {
    var request = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = flowParameters(request, descriptor);
    var stageContext = okapiStageContext(FLOW_STAGE_ID, flowParameters, contextData);

    doNothing().when(kafkaEventPublisher).send(eq(TOPIC_NAME), messageKeyCaptor.capture(), eventCaptor.capture());

    eventPublisher.execute(stageContext);

    assertThat(eventCaptor.getAllValues()).containsExactlyElementsOf(expectedEvents);
    assertThat(messageKeyCaptor.getAllValues()).containsOnly(TENANT_ID.toString());
  }

  @Test
  void execute_positive_noEventsPublished() {
    var request = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var descriptor = readApplicationDescriptor("json/events/capabilities/desc-with-nothing-to-publish.json");
    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = flowParameters(request, descriptor);
    var stageContext = okapiStageContext(FLOW_STAGE_ID, flowParameters, contextData);

    eventPublisher.execute(stageContext);

    verifyNoInteractions(kafkaEventPublisher);
  }

  @Test
  void execute_positive_upgradeEventForUpdatedModule() {
    var moduleDesc = readModuleDescriptor("json/events/capabilities/be-module-desc-v2.json");
    var installedModuleDesc = readModuleDescriptor("json/events/capabilities/be-module-desc.json");
    var uiModuleDesc = readModuleDescriptor("json/events/capabilities/ui-module-desc-v2.json");
    var installedUiModuleDesc = readModuleDescriptor("json/events/capabilities/ui-module-desc.json");

    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = Map.of(
      PARAM_REQUEST, EntitlementRequest.builder().type(UPGRADE).tenantId(TENANT_ID).build(),
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_ENTITLED_APPLICATION_ID, ENTITLED_APPLICATION_ID,
      PARAM_MODULE_DESCRIPTOR_HOLDERS, List.of(moduleDescriptorHolder(moduleDesc, installedModuleDesc)),
      PARAM_UI_MODULE_DESCRIPTOR_HOLDERS, List.of(moduleDescriptorHolder(uiModuleDesc, installedUiModuleDesc)));
    var stageContext = okapiStageContext(FLOW_STAGE_ID, flowParameters, contextData);
    doNothing().when(kafkaEventPublisher).send(eq(TOPIC_NAME), messageKeyCaptor.capture(), eventCaptor.capture());

    eventPublisher.execute(stageContext);

    var expectedEvents = List.of(
      readCapabilityEvent("json/events/capabilities/module-upgrade-event.json"),
      readCapabilityEvent("json/events/capabilities/ui-module-upgrade-event.json"));
    assertThat(eventCaptor.getAllValues()).containsExactlyElementsOf(expectedEvents);
    assertThat(messageKeyCaptor.getAllValues()).containsOnly(TENANT_ID.toString());
  }

  @Test
  void execute_positive_updateRequestModuleNotChanged() {
    var moduleDesc = readModuleDescriptor("json/events/capabilities/be-module-desc.json");
    var flowParameters = Map.of(
      PARAM_REQUEST, EntitlementRequest.builder().type(UPGRADE).tenantId(TENANT_ID).build(),
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_ENTITLED_APPLICATION_ID, ENTITLED_APPLICATION_ID,
      PARAM_MODULE_DESCRIPTOR_HOLDERS, List.of(moduleDescriptorHolder(moduleDesc, moduleDesc)));

    var stageContext = okapiStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, TENANT_NAME));
    doNothing().when(kafkaEventPublisher).send(eq(TOPIC_NAME), messageKeyCaptor.capture(), eventCaptor.capture());

    eventPublisher.execute(stageContext);

    var expectedEvents = List.of(readCapabilityEvent("json/events/capabilities/unchanged-module-event.json"));
    assertThat(eventCaptor.getAllValues()).containsExactlyElementsOf(expectedEvents);
    assertThat(messageKeyCaptor.getAllValues()).containsOnly(TENANT_ID.toString());
  }

  @Test
  void execute_positive_moduleDescriptorHolderContainNullsForNewAndOldDescriptors() {
    var flowParameters = Map.of(
      PARAM_REQUEST, EntitlementRequest.builder().type(UPGRADE).tenantId(TENANT_ID).build(),
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_ENTITLED_APPLICATION_ID, ENTITLED_APPLICATION_ID,
      PARAM_MODULE_DESCRIPTOR_HOLDERS, List.of(moduleDescriptorHolder(null, null)));
    var stageContext = okapiStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, TENANT_NAME));

    eventPublisher.execute(stageContext);

    verifyNoInteractions(kafkaEventPublisher);
  }

  @Test
  void execute_positive_upgradeEventForNewModule() {
    var moduleDescriptor = readModuleDescriptor("json/events/capabilities/be-module-desc.json");
    var uiModuleDescriptor = readModuleDescriptor("json/events/capabilities/ui-module-desc.json");

    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = Map.of(
      PARAM_REQUEST, EntitlementRequest.builder().type(UPGRADE).tenantId(TENANT_ID).build(),
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_ENTITLED_APPLICATION_ID, ENTITLED_APPLICATION_ID,
      PARAM_MODULE_DESCRIPTOR_HOLDERS, List.of(moduleDescriptorHolder(moduleDescriptor, null)),
      PARAM_UI_MODULE_DESCRIPTOR_HOLDERS, List.of(moduleDescriptorHolder(uiModuleDescriptor, null)));
    var stageContext = okapiStageContext(FLOW_STAGE_ID, flowParameters, contextData);
    doNothing().when(kafkaEventPublisher).send(eq(TOPIC_NAME), messageKeyCaptor.capture(), eventCaptor.capture());

    eventPublisher.execute(stageContext);

    var expectedEvents = List.of(
      readCapabilityEvent("json/events/capabilities/full-app-desc-event-1.json"),
      readCapabilityEvent("json/events/capabilities/full-app-desc-event-2.json"));
    assertThat(eventCaptor.getAllValues()).containsExactlyElementsOf(expectedEvents);
    assertThat(messageKeyCaptor.getAllValues()).containsOnly(TENANT_ID.toString());
  }

  @Test
  void execute_positive_upgradeEventForDeprecatedModule() {
    var installedModuleDescriptor = readModuleDescriptor("json/events/capabilities/be-module-desc.json");
    var installedUiModuleDescriptor = readModuleDescriptor("json/events/capabilities/ui-module-desc.json");

    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = Map.of(
      PARAM_REQUEST, EntitlementRequest.builder().type(UPGRADE).tenantId(TENANT_ID).build(),
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_ENTITLED_APPLICATION_ID, ENTITLED_APPLICATION_ID,
      PARAM_DEPRECATED_MODULE_DESCRIPTORS, List.of(installedModuleDescriptor),
      PARAM_DEPRECATED_UI_MODULE_DESCRIPTORS, List.of(installedUiModuleDescriptor));
    var stageContext = okapiStageContext(FLOW_STAGE_ID, flowParameters, contextData);
    doNothing().when(kafkaEventPublisher).send(eq(TOPIC_NAME), messageKeyCaptor.capture(), eventCaptor.capture());

    eventPublisher.execute(stageContext);

    var expectedEvents = List.of(
      readCapabilityEvent("json/events/capabilities/deprecated-module-upgrade-event.json"),
      readCapabilityEvent("json/events/capabilities/deprecated-ui-module-upgrade-event.json"));
    assertThat(eventCaptor.getAllValues()).containsExactlyElementsOf(expectedEvents);
    assertThat(messageKeyCaptor.getAllValues()).containsOnly(TENANT_ID.toString());
  }

  private static Map<String, Object> flowParameters(EntitlementRequest request, ApplicationDescriptor descriptor) {
    var flowParameters = new HashMap<String, Object>();
    flowParameters.put(PARAM_REQUEST, request);
    flowParameters.put(PARAM_APPLICATION_ID, descriptor.getId());
    flowParameters.put(PARAM_APPLICATION_DESCRIPTOR, descriptor);
    flowParameters.put(PARAM_MODULE_DESCRIPTORS, descriptor.getModuleDescriptors());
    flowParameters.put(PARAM_UI_MODULE_DESCRIPTORS, descriptor.getUiModuleDescriptors());
    return flowParameters;
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
