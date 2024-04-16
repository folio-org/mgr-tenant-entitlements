package org.folio.entitlement.service.flow;

import static java.util.Arrays.asList;
import static org.folio.entitlement.utils.FlowUtils.combineStages;
import static org.folio.flow.api.NoOpStage.noOpStage;

import java.util.Map;
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
import org.folio.entitlement.service.stage.ApplicationDependencySaver;
import org.folio.entitlement.service.stage.ApplicationDescriptorValidator;
import org.folio.entitlement.service.stage.ApplicationDiscoveryLoader;
import org.folio.entitlement.service.stage.ApplicationFlowInitializer;
import org.folio.entitlement.service.stage.CancellationFailedApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.CancelledApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.EntitleApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.EntitleRequestDependencyValidator;
import org.folio.entitlement.service.stage.FailedApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.SkippedApplicationFlowFinalizer;
import org.folio.flow.api.DynamicStage;
import org.folio.flow.api.Flow;
import org.folio.flow.api.ParallelStage;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;
import org.folio.flow.model.FlowExecutionStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntitleApplicationFlowFactory implements ApplicationFlowFactory {

  private final ApplicationDescriptorValidator applicationDescriptorValidator;
  private final ApplicationDependencySaver applicationDependencySaver;
  private final ApplicationDiscoveryLoader applicationDiscoveryLoader;
  private final EntitleRequestDependencyValidator entitleRequestDependencyValidator;
  private final KafkaTenantTopicCreator kafkaTenantTopicCreator;

  // publishers
  private final SystemUserEventPublisher sysUserEventPublisher;
  private final ScheduledJobEventPublisher scheduledJobEventPublisher;
  private final CapabilitiesEventPublisher capabilitiesEventPublisher;

  // application flow database logging stages
  private final ApplicationFlowInitializer flowInitializer;
  private final FailedApplicationFlowFinalizer failedFlowFinalizer;
  private final SkippedApplicationFlowFinalizer skippedFlowFinalizer;
  private final EntitleApplicationFlowFinalizer finishedFlowFinalizer;
  private final CancelledApplicationFlowFinalizer cancelledFlowFinalizer;
  private final CancellationFailedApplicationFlowFinalizer cancellationFailedFlowFinalizer;

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
   * @param flowId - application flow identifier as {@link UUID} object
   * @param strategy - flow execution strategy as {@link FlowExecutionStrategy} enum value
   * @return created {@link Flow} for future execution
   */
  @Override
  public Flow createFlow(Object flowId, FlowExecutionStrategy strategy, Map<?, ?> additionalFlowParameters) {
    return Flow.builder()
      .id(flowId)
      .stage(flowInitializer)
      .stage(applicationDescriptorValidator)
      .stage(applicationDependencySaver)
      .stage(entitleRequestDependencyValidator)
      .stage(applicationDiscoveryLoader)
      .stage(kafkaTenantTopicCreator)
      .stage(Flow.builder()
        .id(flowId + "/ModuleInstaller")
        .stage(combineStages("ParallelResourcesCreator", asList(kongRouteCreator, keycloakAuthResourceCreator)))
        .stage(getModuleInstaller())
        .stage(getEventPublisherStage())
        .executionStrategy(strategy)
        .build())
      .stage(finishedFlowFinalizer)
      .onFlowSkip(skippedFlowFinalizer)
      .onFlowError(failedFlowFinalizer)
      .onFlowCancellation(cancelledFlowFinalizer)
      .onFlowCancellationError(cancellationFailedFlowFinalizer)
      .executionStrategy(strategy)
      .flowParameters(additionalFlowParameters)
      .build();
  }

  public String getName() {
    return this.getClass().getSimpleName();
  }

  private Stage<? extends StageContext> getModuleInstaller() {
    if (okapiModuleInstallerFlowProvider != null) {
      return DynamicStage.of("OkapiModuleInstallerProvider", okapiModuleInstallerFlowProvider::prepareFlow);
    }

    if (folioModuleInstallerFlowProvider != null) {
      return DynamicStage.of("FolioModuleInstallerProvider", folioModuleInstallerFlowProvider::prepareFlow);
    }

    return noOpStage();
  }

  private ParallelStage getEventPublisherStage() {
    return ParallelStage.of("EntitlementEventPublisher",
      scheduledJobEventPublisher, capabilitiesEventPublisher, sysUserEventPublisher);
  }
}
