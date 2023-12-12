package org.folio.entitlement.service.flow;

import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_ID;
import static org.folio.entitlement.service.flow.EntitlementFlowProviderUtils.combineStages;
import static org.folio.flow.api.NoOpStage.noOpStage;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.folio.entitlement.integration.folio.ModuleInstallationFlowProvider;
import org.folio.entitlement.integration.kafka.CapabilitiesEventPublisher;
import org.folio.entitlement.integration.kafka.KafkaTenantTopicCreator;
import org.folio.entitlement.integration.kafka.ScheduledJobEventPublisher;
import org.folio.entitlement.integration.kafka.SystemUserEventPublisher;
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceCreator;
import org.folio.entitlement.integration.kong.KongRouteCreator;
import org.folio.entitlement.integration.okapi.OkapiModuleInstallerFlowProvider;
import org.folio.entitlement.service.stage.ApplicationDescriptorLoader;
import org.folio.entitlement.service.stage.ApplicationDescriptorValidator;
import org.folio.entitlement.service.stage.ApplicationDiscoveryLoader;
import org.folio.entitlement.service.stage.CancellationFailedFlowFinalizer;
import org.folio.entitlement.service.stage.CancelledFlowFinalizer;
import org.folio.entitlement.service.stage.EntitlementDependencyValidator;
import org.folio.entitlement.service.stage.EntitlementFlowFinalizer;
import org.folio.entitlement.service.stage.EntitlementFlowInitializer;
import org.folio.entitlement.service.stage.FailedFlowFinalizer;
import org.folio.entitlement.service.stage.TenantLoader;
import org.folio.flow.api.DynamicStage;
import org.folio.flow.api.Flow;
import org.folio.flow.api.ParallelStage;
import org.folio.flow.api.Stage;
import org.folio.flow.model.FlowExecutionStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntitlementFlowProvider {

  private final TenantLoader tenantLoader;
  private final ApplicationDescriptorLoader applicationDescriptorLoader;
  private final ApplicationDescriptorValidator applicationDescriptorValidator;
  private final ApplicationDiscoveryLoader applicationDiscoveryLoader;
  private final EntitlementDependencyValidator entitlementDependencyValidator;
  private final ScheduledJobEventPublisher scheduledJobEventPublisher;
  private final KafkaTenantTopicCreator kafkaTenantTopicCreator;
  private final CapabilitiesEventPublisher capabilitiesEventPublisher;
  private final SystemUserEventPublisher sysUserEventPublisher;
  private final EntitlementFlowInitializer entitlementFlowInitializer;
  private final EntitlementFlowFinalizer entitlementFlowFinalizer;
  private final FailedFlowFinalizer failedFlowFinalizer;
  private final CancelledFlowFinalizer cancelledFlowFinalizer;
  private final CancellationFailedFlowFinalizer cancellationFailedFlowFinalizer;

  @Setter(onMethod_ = @Autowired(required = false))
  private OkapiModuleInstallerFlowProvider okapiModuleInstallerFlowProvider;

  @Setter(onMethod_ = @Autowired(required = false))
  private KongRouteCreator kongRouteCreator;

  @Setter(onMethod_ = @Autowired(required = false))
  private KeycloakAuthResourceCreator keycloakAuthResourceCreator;

  @Setter(onMethod_ = @Autowired(required = false))
  private ModuleInstallationFlowProvider folioModuleInstallerFlowProvider;

  /**
   * Creates a {@link Flow} object for application installation.
   *
   * @param applicationFlowId - application flow identifier as {@link UUID} object
   * @param applicationId - application identifier as {@link String} object
   * @param strategy - flow execution strategy as {@link FlowExecutionStrategy} enum value
   * @return created {@link Flow} for future execution
   */
  public Flow prepareFlow(Object applicationFlowId, String applicationId, FlowExecutionStrategy strategy) {
    return Flow.builder()
      .id(applicationFlowId)
      .stage(entitlementFlowInitializer)
      .stage(tenantLoader)
      .stage(applicationDescriptorLoader)
      .stage(applicationDescriptorValidator)
      .stage(entitlementDependencyValidator)
      .stage(applicationDiscoveryLoader)
      .stage(kafkaTenantTopicCreator)
      .stage(Flow.builder()
        .id(applicationFlowId + "/module-installer")
        .stage(combineStages(kongRouteCreator, keycloakAuthResourceCreator))
        .stage(getModuleInstaller())
        .stage(ParallelStage.of("entitlement-event-publisher",
          scheduledJobEventPublisher, capabilitiesEventPublisher, sysUserEventPublisher))
        .executionStrategy(strategy)
        .build())
      .stage(entitlementFlowFinalizer)
      .onFlowError(failedFlowFinalizer)
      .onFlowCancellation(cancelledFlowFinalizer)
      .onFlowCancellationError(cancellationFailedFlowFinalizer)
      .executionStrategy(strategy)
      .flowParameter(PARAM_APP_ID, applicationId)
      .build();
  }

  private Stage getModuleInstaller() {
    if (okapiModuleInstallerFlowProvider != null) {
      return DynamicStage.of(okapiModuleInstallerFlowProvider::prepareFlow);
    }

    if (folioModuleInstallerFlowProvider != null) {
      return DynamicStage.of(folioModuleInstallerFlowProvider::prepareFlow);
    }

    return noOpStage();
  }
}
