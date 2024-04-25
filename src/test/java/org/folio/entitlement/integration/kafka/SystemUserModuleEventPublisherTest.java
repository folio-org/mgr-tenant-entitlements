package org.folio.entitlement.integration.kafka;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.integration.kafka.model.ResourceEventType.CREATE;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.systemUserTenantTopic;
import static org.folio.entitlement.support.TestValues.moduleFlowParameters;
import static org.folio.entitlement.support.TestValues.moduleStageContext;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.UserDescriptor;
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
class SystemUserModuleEventPublisherTest {

  @InjectMocks private SystemUserModuleEventPublisher moduleEventPublisher;
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
  void execute_positive() {
    var moduleId = "mod-foo-1.2.3";
    var moduleDescriptor = moduleDescriptor(moduleId, UserDescriptor.of("system", List.of("foo.entities.post")));
    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(ENTITLE).build();
    var flowParameters = moduleFlowParameters(request, moduleDescriptor);
    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, contextData);

    moduleEventPublisher.execute(stageContext);

    var systemUserEvent = SystemUserEvent.of("mod-foo", "system", List.of("foo.entities.post"));
    verify(kafkaEventPublisher).send(systemUserTenantTopic(), moduleId, resourceEvent(systemUserEvent));
  }

  @Test
  void execute_positive_noSystemUsersDefined() {
    var moduleDescriptor = new ModuleDescriptor().id("mod-foo-1.0.0");
    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(ENTITLE).build();
    var flowParameters = moduleFlowParameters(request, moduleDescriptor);
    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
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

    assertThat(result).isEqualTo("mod-foo-1.0.0-systemUserModuleEventPublisher");
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
