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
import static org.folio.entitlement.support.TestConstants.systemUserTenantTopic;
import static org.folio.entitlement.support.TestValues.moduleDescriptorHolder;
import static org.folio.entitlement.support.TestValues.okapiStageContext;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.UserDescriptor;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.integration.kafka.model.SystemUserEvent;
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
class SystemUserEventPublisherTest {

  private static final String MOD_FOO_V1_ID = "mod-foo-1.0.0";
  private static final String MOD_FOO_V2_ID = "mod-foo-2.0.0";
  private static final String MOD_BAR_ID = "mod-bar-1.0.0";
  private static final String SYS_USER_TYPE = "system";

  @InjectMocks private SystemUserEventPublisher publisher;
  @Mock private KafkaEventPublisher kafkaEventPublisher;

  @BeforeEach
  void setUp() {
    System.setProperty("env", "test-env");
  }

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
    System.clearProperty("env");
  }

  @Test
  void execute_positive_entitleEvent() {
    var modFoo = moduleDescriptor(MOD_FOO_V1_ID, userDescriptor("foo.entities.post"));
    var modBar = moduleDescriptor(MOD_BAR_ID, null);
    var flowParameters = Map.of(
      PARAM_REQUEST, request(ENTITLE),
      PARAM_MODULE_DESCRIPTORS, List.of(modFoo, modBar),
      PARAM_APPLICATION_ID, APPLICATION_ID);

    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = okapiStageContext(FLOW_ID, flowParameters, contextData);

    publisher.execute(stageContext);

    var expectedMessageKey = TENANT_ID.toString();
    var systemUserEvent = SystemUserEvent.of("mod-foo", SYS_USER_TYPE, List.of("foo.entities.post"));
    verify(kafkaEventPublisher).send(systemUserTenantTopic(), expectedMessageKey, resourceEvent(systemUserEvent));
  }

  @Test
  void execute_positive_updateRequest() {
    var modFooV2 = moduleDescriptor(MOD_FOO_V2_ID, userDescriptor("foo.item.v2.get", "foo.item.v2.post"));
    var modFooV1 = moduleDescriptor(MOD_FOO_V1_ID, userDescriptor("foo.item.get", "foo.item.post"));
    var flowParameters = Map.of(
      PARAM_REQUEST, request(UPGRADE),
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_ENTITLED_APPLICATION_ID, ENTITLED_APPLICATION_ID,
      PARAM_MODULE_DESCRIPTOR_HOLDERS, List.of(moduleDescriptorHolder(modFooV2, modFooV1)),
      PARAM_DEPRECATED_MODULE_DESCRIPTORS, List.of(moduleDescriptor(MOD_BAR_ID, userDescriptor("bar.item.get"))));

    var contextParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = okapiStageContext(FLOW_ID, flowParameters, contextParameters);

    publisher.execute(stageContext);

    var expectedResourceEvent = ResourceEvent.<SystemUserEvent>builder()
      .type(UPDATE).tenant(TENANT_NAME).resourceName("System user")
      .newValue(SystemUserEvent.of("mod-foo", SYS_USER_TYPE, List.of("foo.item.v2.get", "foo.item.v2.post")))
      .oldValue(SystemUserEvent.of("mod-foo", SYS_USER_TYPE, List.of("foo.item.get", "foo.item.post")))
      .build();
    var expectedMessageKey = TENANT_ID.toString();
    verify(kafkaEventPublisher).send(systemUserTenantTopic(), expectedMessageKey, expectedResourceEvent);

    var expectedDeprecatedResourceEvent = ResourceEvent.<SystemUserEvent>builder()
      .type(DELETE).tenant(TENANT_NAME).resourceName("System user")
      .oldValue(SystemUserEvent.of("mod-bar", SYS_USER_TYPE, List.of("bar.item.get")))
      .build();
    verify(kafkaEventPublisher).send(systemUserTenantTopic(), expectedMessageKey, expectedDeprecatedResourceEvent);
  }

  @Test
  void execute_positive_noSystemUsersDefined() {
    var flowParameters = Map.of(
      PARAM_REQUEST, request(ENTITLE),
      PARAM_MODULE_DESCRIPTORS, List.of(moduleDescriptor(MOD_BAR_ID, null)),
      PARAM_APPLICATION_ID, APPLICATION_ID);

    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = okapiStageContext(FLOW_ID, flowParameters, contextData);

    publisher.execute(stageContext);

    verifyNoInteractions(kafkaEventPublisher);
  }

  private static EntitlementRequest request(EntitlementType type) {
    return EntitlementRequest.builder().tenantId(TENANT_ID).type(type).build();
  }

  private static ModuleDescriptor moduleDescriptor(String id, UserDescriptor userDescriptor) {
    return new ModuleDescriptor().id(id).user(userDescriptor);
  }

  private static UserDescriptor userDescriptor(String ... permissions) {
    var userDescriptor = new UserDescriptor();
    userDescriptor.setType(SYS_USER_TYPE);
    userDescriptor.setPermissions(List.of(permissions));
    return userDescriptor;
  }

  private static ResourceEvent<SystemUserEvent> resourceEvent(SystemUserEvent entry) {
    return ResourceEvent.<SystemUserEvent>builder()
      .type(CREATE)
      .tenant(TENANT_NAME)
      .resourceName("System user")
      .newValue(entry)
      .build();
  }
}
