package org.folio.entitlement.integration.kafka;

import static java.util.Collections.emptyMap;
import static java.util.Optional.empty;
import static java.util.Optional.of;
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
import static org.folio.entitlement.support.TestConstants.systemUserTenantCollectionTopic;
import static org.folio.entitlement.support.TestConstants.systemUserTenantTopic;
import static org.folio.entitlement.support.TestValues.moduleFlowParameters;
import static org.folio.entitlement.support.TestValues.moduleStageContext;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.UserDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.kafka.configuration.TenantEntitlementKafkaProperties;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.integration.kafka.model.SystemUserEvent;
import org.folio.entitlement.support.TestUtils;
import org.folio.entitlement.utils.SystemUserEventProvider;
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
class SystemUserModuleEventPublisherTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final String MODULE_ID_V2 = "mod-foo-2.0.0";
  private static final String MODULE_NAME = "mod-foo";
  private static final String SYS_USER_TYPE = "system";

  @InjectMocks private SystemUserModuleEventPublisher moduleEventPublisher;
  @Mock private KafkaEventPublisher kafkaEventPublisher;
  @Mock private SystemUserEventProvider systemUserEventProvider;
  @Mock private TenantEntitlementKafkaProperties tenantEntitlementKafkaProperties;

  @BeforeEach
  void setUp() {
    System.setProperty("env", "test-env");
    moduleEventPublisher.setKafkaEventPublisher(kafkaEventPublisher);
    moduleEventPublisher.setTenantEntitlementKafkaProperties(tenantEntitlementKafkaProperties);
  }

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
    System.clearProperty("env");
  }

  @Test
  void execute_positive() {
    var userDescriptor = userDescriptor("foo.entities.post");
    var moduleDescriptor = moduleDescriptor(MODULE_ID, userDescriptor);
    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(ENTITLE).build();
    var flowParameters = moduleFlowParameters(request, moduleDescriptor);
    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);

    var systemUserEvent = SystemUserEvent.of(MODULE_NAME, SYS_USER_TYPE, List.of("foo.entities.post"));
    when(systemUserEventProvider.getSystemUserEvent(moduleDescriptor)).thenReturn(of(systemUserEvent));
    when(systemUserEventProvider.getSystemUserEvent(null)).thenReturn(empty());
    when(tenantEntitlementKafkaProperties.isProducerTenantCollection()).thenReturn(false);

    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, contextData);
    moduleEventPublisher.execute(stageContext);

    var expectedMessageKey = TENANT_ID.toString();
    verify(kafkaEventPublisher).send(systemUserTenantTopic(), expectedMessageKey, resourceEvent(systemUserEvent));
  }

  @Test
  void execute_positive_useTenantCollectionTopic() {
    var userDescriptor = userDescriptor("foo.entities.post");
    var moduleDescriptor = moduleDescriptor(MODULE_ID, userDescriptor);
    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(ENTITLE).build();
    var flowParameters = moduleFlowParameters(request, moduleDescriptor);
    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);

    var systemUserEvent = SystemUserEvent.of(MODULE_NAME, SYS_USER_TYPE, List.of("foo.entities.post"));
    when(systemUserEventProvider.getSystemUserEvent(moduleDescriptor)).thenReturn(of(systemUserEvent));
    when(systemUserEventProvider.getSystemUserEvent(null)).thenReturn(empty());
    when(tenantEntitlementKafkaProperties.isProducerTenantCollection()).thenReturn(true);

    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, contextData);
    moduleEventPublisher.execute(stageContext);

    var expectedMessageKey = TENANT_ID.toString();
    verify(kafkaEventPublisher).send(systemUserTenantCollectionTopic(),
      expectedMessageKey, resourceEvent(systemUserEvent));
  }

  @Test
  void execute_positive_noSystemUsersDefined() {
    var moduleDescriptor = new ModuleDescriptor().id("mod-foo-1.0.0");
    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(ENTITLE).build();
    var flowParameters = moduleFlowParameters(request, moduleDescriptor);
    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);

    when(systemUserEventProvider.getSystemUserEvent(null)).thenReturn(empty());
    when(systemUserEventProvider.getSystemUserEvent(moduleDescriptor)).thenReturn(empty());
    when(tenantEntitlementKafkaProperties.isProducerTenantCollection()).thenReturn(false);

    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, contextData);
    moduleEventPublisher.execute(stageContext);

    verifyNoInteractions(kafkaEventPublisher);
  }

  @Test
  void execute_positive_upgradeRequestWithChangedModule() {
    var v1ModuleDescriptor = moduleDescriptor(MODULE_ID, userDescriptor("foo.entities.post"));
    var v2ModuleDescriptor = moduleDescriptor(MODULE_ID_V2, userDescriptor("foo.v2.entities.post"));
    var flowParameters = Map.of(
      PARAM_REQUEST, EntitlementRequest.builder().tenantId(TENANT_ID).type(UPGRADE).build(),
      PARAM_MODULE_DESCRIPTOR, v2ModuleDescriptor,
      PARAM_INSTALLED_MODULE_DESCRIPTOR, v1ModuleDescriptor,
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_ENTITLED_APPLICATION_ID, ENTITLED_APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID);
    var v1UserEvent = SystemUserEvent.of(MODULE_NAME, SYS_USER_TYPE, List.of("foo.entities.post"));
    var v2UserEvent = SystemUserEvent.of(MODULE_NAME, SYS_USER_TYPE, List.of("foo.v2.entities.post"));

    when(systemUserEventProvider.getSystemUserEvent(v1ModuleDescriptor)).thenReturn(of(v1UserEvent));
    when(systemUserEventProvider.getSystemUserEvent(v2ModuleDescriptor)).thenReturn(of(v2UserEvent));
    when(tenantEntitlementKafkaProperties.isProducerTenantCollection()).thenReturn(false);

    var stageContext = moduleStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, TENANT_NAME));
    moduleEventPublisher.execute(stageContext);

    var expectedResourceEvent = ResourceEvent.<SystemUserEvent>builder()
      .type(UPDATE).tenant(TENANT_NAME).resourceName("System user")
      .newValue(SystemUserEvent.of(MODULE_NAME, SYS_USER_TYPE, List.of("foo.v2.entities.post")))
      .oldValue(SystemUserEvent.of(MODULE_NAME, SYS_USER_TYPE, List.of("foo.entities.post")))
      .build();

    verify(kafkaEventPublisher).send(systemUserTenantTopic(), TENANT_ID.toString(), expectedResourceEvent);
  }

  @Test
  void execute_positive_upgradeRequestWithNotChangedModule() {
    var flowParameters = Map.of(
      PARAM_REQUEST, EntitlementRequest.builder().tenantId(TENANT_ID).type(UPGRADE).build(),
      PARAM_MODULE_DESCRIPTOR, moduleDescriptor(MODULE_ID, userDescriptor("foo.entities.post")),
      PARAM_INSTALLED_MODULE_DESCRIPTOR, moduleDescriptor(MODULE_ID, userDescriptor("foo.entities.post")),
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_ENTITLED_APPLICATION_ID, ENTITLED_APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID);
    var stageContext = moduleStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, TENANT_NAME));

    moduleEventPublisher.execute(stageContext);
    verifyNoInteractions(kafkaEventPublisher);
  }

  @Test
  void execute_positive_upgradeRequestWithDeprecatedModule() {
    var userDescriptor = userDescriptor("foo.entities.post");
    var moduleDescriptor = moduleDescriptor(MODULE_ID, userDescriptor);
    var flowParameters = Map.of(
      PARAM_REQUEST, EntitlementRequest.builder().tenantId(TENANT_ID).type(ENTITLE).build(),
      PARAM_INSTALLED_MODULE_DESCRIPTOR, moduleDescriptor,
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_ENTITLED_APPLICATION_ID, ENTITLED_APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID);

    var systemUserEvent = SystemUserEvent.of(MODULE_NAME, SYS_USER_TYPE, List.of("foo.entities.post"));
    when(systemUserEventProvider.getSystemUserEvent(moduleDescriptor)).thenReturn(of(systemUserEvent));
    when(systemUserEventProvider.getSystemUserEvent(null)).thenReturn(empty());
    when(tenantEntitlementKafkaProperties.isProducerTenantCollection()).thenReturn(false);

    var stageContext = moduleStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, TENANT_NAME));
    moduleEventPublisher.execute(stageContext);

    var expectedResourceEvent = ResourceEvent.<SystemUserEvent>builder()
      .type(DELETE).tenant(TENANT_NAME).resourceName("System user")
      .oldValue(SystemUserEvent.of(MODULE_NAME, SYS_USER_TYPE, List.of("foo.entities.post")))
      .build();

    verify(kafkaEventPublisher).send(systemUserTenantTopic(), TENANT_ID.toString(), expectedResourceEvent);
  }

  @Test
  void getStageName_positive() {
    var request = EntitlementRequest.builder().type(ENTITLE).build();
    var moduleDescriptor = new ModuleDescriptor().id("mod-foo-1.0.0");
    var flowParameters = moduleFlowParameters(request, moduleDescriptor);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    var result = moduleEventPublisher.getStageName(stageContext);

    assertThat(result).isEqualTo("mod-foo-1.0.0-systemUserModuleEventPublisher");
  }

  @Test
  void getTopicNameByTenantCollection_positive() {
    var actual = moduleEventPublisher.getTopicNameByTenantCollection();

    assertThat(actual).isEqualTo("test-env.ALL.mgr-tenant-entitlements.system-user");
  }

  private static ModuleDescriptor moduleDescriptor(String moduleId, UserDescriptor userDescriptor) {
    //noinspection deprecation
    return new ModuleDescriptor().id(moduleId).user(userDescriptor);
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
