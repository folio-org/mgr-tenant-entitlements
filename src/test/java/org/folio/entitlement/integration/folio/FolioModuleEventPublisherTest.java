package org.folio.entitlement.integration.folio;

import static java.util.Collections.emptyMap;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.UPGRADE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_INSTALLED_MODULE_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_ID;
import static org.folio.entitlement.support.TestValues.moduleStageContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Map;
import java.util.UUID;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.folio.stage.FolioModuleEventPublisher;
import org.folio.entitlement.integration.kafka.EntitlementEventPublisher;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FolioModuleEventPublisherTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final String FLOW_ID = UUID.randomUUID().toString();

  @InjectMocks private FolioModuleEventPublisher folioModuleEventPublisher;
  @Mock private EntitlementEventPublisher entitlementEventPublisher;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(entitlementEventPublisher);
  }

  @Test
  void execute_positive() {
    var tenantId = randomUUID();
    var tenantName = "tenantName";
    var request = EntitlementRequest.builder().type(ENTITLE).tenantId(tenantId).build();
    var desc = new ModuleDescriptor().id(MODULE_ID);
    var flowParameters = Map.of(PARAM_REQUEST, request, PARAM_MODULE_ID, MODULE_ID, PARAM_MODULE_DESCRIPTOR, desc);
    var stageContext = moduleStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, tenantName));

    folioModuleEventPublisher.execute(stageContext);

    var expectedEvent = new EntitlementEvent(ENTITLE.name(), MODULE_ID, tenantName, tenantId);
    verify(entitlementEventPublisher).publish(expectedEvent);
  }

  @Test
  void execute_positive_moduleNotChanged() {
    var tenantId = randomUUID();
    var tenantName = "tenantName";
    var flowParameters = Map.of(
      PARAM_REQUEST, EntitlementRequest.builder().type(UPGRADE).tenantId(tenantId).build(),
      PARAM_MODULE_ID, MODULE_ID,
      PARAM_MODULE_DESCRIPTOR, new ModuleDescriptor().id(MODULE_ID),
      PARAM_INSTALLED_MODULE_DESCRIPTOR, new ModuleDescriptor().id(MODULE_ID));
    var stageContext = moduleStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, tenantName));

    folioModuleEventPublisher.execute(stageContext);

    verifyNoMoreInteractions(entitlementEventPublisher);
  }

  @Test
  void execute_positive_moduleUpdated() {
    var tenantId = randomUUID();
    var tenantName = "tenantName";
    var installedModuleId = "mod-foo-0.0.9";
    var flowParameters = Map.of(
      PARAM_REQUEST, EntitlementRequest.builder().type(UPGRADE).tenantId(tenantId).build(),
      PARAM_MODULE_ID, MODULE_ID,
      PARAM_MODULE_DESCRIPTOR, new ModuleDescriptor().id(MODULE_ID),
      PARAM_INSTALLED_MODULE_DESCRIPTOR, new ModuleDescriptor().id(installedModuleId));
    var stageContext = moduleStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, tenantName));

    folioModuleEventPublisher.execute(stageContext);

    var expectedEvent = new EntitlementEvent(UPGRADE.name(), MODULE_ID, tenantName, tenantId);
    verify(entitlementEventPublisher).publish(expectedEvent);

    var expectedRevokeEvent = new EntitlementEvent(REVOKE.name(), installedModuleId, tenantName, tenantId);
    verify(entitlementEventPublisher).publish(expectedRevokeEvent);
  }

  @Test
  void execute_positive_moduleDeprecated() {
    var tenantId = randomUUID();
    var tenantName = "tenantName";
    var flowParameters = Map.of(
      PARAM_REQUEST, EntitlementRequest.builder().type(UPGRADE).tenantId(tenantId).build(),
      PARAM_MODULE_ID, MODULE_ID,
      PARAM_INSTALLED_MODULE_DESCRIPTOR, new ModuleDescriptor().id(MODULE_ID));
    var stageContext = moduleStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, tenantName));

    folioModuleEventPublisher.execute(stageContext);

    var expectedEvent = new EntitlementEvent(REVOKE.name(), MODULE_ID, tenantName, tenantId);
    verify(entitlementEventPublisher).publish(expectedEvent);
  }

  @Test
  void execute_negative() {
    var message = "exception";
    doThrow(new RuntimeException(message)).when(entitlementEventPublisher).publish(any(EntitlementEvent.class));

    var request = EntitlementRequest.builder().type(ENTITLE).tenantId(randomUUID()).build();
    var desc = new ModuleDescriptor().id(MODULE_ID);
    var flowParameters = Map.of(PARAM_REQUEST, request, PARAM_MODULE_ID, MODULE_ID, PARAM_MODULE_DESCRIPTOR, desc);
    var contextParameters = Map.of(PARAM_TENANT_NAME, PARAM_TENANT_NAME);
    var context = moduleStageContext(FLOW_ID, flowParameters, contextParameters);

    assertThatThrownBy(() -> folioModuleEventPublisher.execute(context))
      .isInstanceOf(RuntimeException.class)
      .hasMessage(message);
  }

  @Test
  void cancel_positive_entitleRequest() {
    var tenantId = randomUUID();
    var tenantName = "tenantName";
    var request = EntitlementRequest.builder().type(ENTITLE).tenantId(tenantId).build();
    var desc = new ModuleDescriptor().id(MODULE_ID);
    var flowParameters = Map.of(PARAM_REQUEST, request, PARAM_MODULE_ID, MODULE_ID, PARAM_MODULE_DESCRIPTOR, desc);
    var stageContext = moduleStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, tenantName));

    folioModuleEventPublisher.cancel(stageContext);
    var expectedEvent = new EntitlementEvent(REVOKE.name(), MODULE_ID, tenantName, tenantId);
    verify(entitlementEventPublisher).publish(expectedEvent);
  }

  @Test
  void cancel_positive_revokeRequest() {
    var tenantId = randomUUID();
    var tenantName = "tenantName";
    var request = EntitlementRequest.builder().type(REVOKE).tenantId(tenantId).build();
    var desc = new ModuleDescriptor().id(MODULE_ID);
    var flowParameters = Map.of(PARAM_REQUEST, request, PARAM_MODULE_ID, MODULE_ID, PARAM_MODULE_DESCRIPTOR, desc);
    var stageContext = moduleStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, tenantName));

    folioModuleEventPublisher.cancel(stageContext);
    verify(entitlementEventPublisher, never()).publish(any());
  }

  @Test
  void getStageName_positive() {
    var flowParameters = Map.of(PARAM_MODULE_ID, MODULE_ID);
    var stageContext = moduleStageContext(FLOW_ID, flowParameters, emptyMap());
    var result = folioModuleEventPublisher.getStageName(stageContext);
    assertThat(result).isEqualTo(MODULE_ID + "-folioModuleEventPublisher");
  }
}
