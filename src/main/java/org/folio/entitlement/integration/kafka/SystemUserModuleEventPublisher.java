package org.folio.entitlement.integration.kafka;

import static org.folio.entitlement.integration.kafka.model.ResourceEventType.CREATE;
import static org.folio.integration.kafka.KafkaUtils.getTenantTopicName;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.integration.kafka.model.SystemUserEvent;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class SystemUserModuleEventPublisher extends ModuleDatabaseLoggingStage {

  private static final String SYS_USER_TOPIC = "mgr-tenant-entitlements.system-user";
  private static final String SYS_USER_RESOURCE_NAME = "System user";

  private final KafkaEventPublisher kafkaEventPublisher;

  @Override
  public void execute(ModuleStageContext context) {
    var descriptor = context.getModuleDescriptor();
    var tenantName = context.getTenantName();

    if (descriptor.getUser() == null) {
      return;
    }

    var moduleName = context.getModuleName();
    sendEvent(moduleName, tenantName, descriptor);
  }

  /**
   * Sends a system user event for provided {@link ModuleDescriptor} object.
   *
   * @param moduleDescriptor - {@link ModuleDescriptor} object
   * @param moduleName - tenant identifier
   * @param tenantName - tenant name
   */
  public void sendEvent(String moduleName, String tenantName, ModuleDescriptor moduleDescriptor) {
    var systemUser = moduleDescriptor.getUser();
    var moduleId = moduleDescriptor.getId();
    var payload = SystemUserEvent.of(moduleName, systemUser.getType(), systemUser.getPermissions());

    var event = ResourceEvent.<SystemUserEvent>builder()
      .type(CREATE)
      .tenant(tenantName)
      .resourceName(SYS_USER_RESOURCE_NAME)
      .newValue(payload)
      .build();

    kafkaEventPublisher.send(getTenantTopicName(SYS_USER_TOPIC, tenantName), moduleId, event);
  }
}
