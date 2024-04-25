package org.folio.entitlement.integration.kafka;

import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.integration.kafka.model.ResourceEventType.CREATE;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.systemUserTenantTopic;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.folio.entitlement.support.TestValues.flowParameters;
import static org.folio.entitlement.support.TestValues.module;
import static org.folio.entitlement.support.TestValues.simpleApplicationDescriptor;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.UserDescriptor;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.integration.kafka.model.SystemUserEvent;
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
class SystemUserEventPublisherTest {

  private static final String MODULE_FOO_ID = "mod-foo-1.2.3";
  private static final String MODULE_BAR_ID = "mod-bar-1.0.0";

  private SystemUserEventPublisher publisher;
  @Mock private KafkaEventPublisher kafkaEventPublisher;

  @BeforeEach
  void setUp() {
    System.setProperty("env", "test-env");
    var moduleEventPublisher = new SystemUserModuleEventPublisher(kafkaEventPublisher);
    this.publisher = new SystemUserEventPublisher(moduleEventPublisher);
  }

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
    System.clearProperty("env");
  }

  @Test
  void execute_positive() {
    var flowParameters = flowParameters(request(ENTITLE), appDescriptorWithModuleSystemUser());
    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = appStageContext(FLOW_ID, flowParameters, contextData);

    publisher.execute(stageContext);

    var systemUserEvent = SystemUserEvent.of("mod-foo", "system", List.of("foo.entities.post"));
    verify(kafkaEventPublisher).send(systemUserTenantTopic(), MODULE_FOO_ID, resourceEvent(systemUserEvent));
  }

  @Test
  void execute_positive_noSystemUsersDefined() {
    var flowParameters = flowParameters(request(ENTITLE), simpleApplicationDescriptor(APPLICATION_ID));
    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = appStageContext(FLOW_ID, flowParameters, contextData);

    publisher.execute(stageContext);

    verifyNoInteractions(kafkaEventPublisher);
  }

  private static ApplicationDescriptor appDescriptorWithModuleSystemUser() {
    var moduleDescriptors = List.of(
      moduleDescriptor(MODULE_FOO_ID, UserDescriptor.of("system", List.of("foo.entities.post"))),
      moduleDescriptor(MODULE_BAR_ID, null)
    );
    var modules = List.of(
      module("mod-foo", "1.2.3"),
      module("mod-bar", "1.0.0")
    );

    return applicationDescriptor()
      .moduleDescriptors(moduleDescriptors)
      .modules(modules);
  }

  private static EntitlementRequest request(EntitlementType type) {
    return EntitlementRequest.builder().tenantId(TENANT_ID).type(type).build();
  }

  private static ModuleDescriptor moduleDescriptor(String moduleId, UserDescriptor userDescriptor) {
    return new ModuleDescriptor().id(moduleId).user(userDescriptor);
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
