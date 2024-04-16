package org.folio.entitlement.integration.kafka;

import static java.util.stream.Collectors.toMap;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.integration.kafka.model.ResourceEventType.CREATE;
import static org.folio.integration.kafka.KafkaUtils.getTenantTopicName;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.am.model.Module;
import org.folio.entitlement.integration.folio.ApplicationStageContext;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.integration.kafka.model.SystemUserEvent;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class SystemUserEventPublisher extends DatabaseLoggingStage<ApplicationStageContext> {

  private static final String SYS_USER_TOPIC = "mgr-tenant-entitlements.system-user";
  private static final String SYS_USER_RESOURCE_NAME = "System user";

  private final KafkaEventPublisher kafkaEventPublisher;

  @Override
  public void execute(ApplicationStageContext context) {
    if (context.getEntitlementRequest().getType() == UPGRADE) {
      return;
    }

    var descriptor = context.getApplicationDescriptor();
    var tenantName = context.getTenantName();

    var moduleIdNameMap = getModuleIdNames(descriptor);

    toStream(descriptor.getModuleDescriptors())
      .filter(SystemUserEventPublisher::hasSystemUser)
      .forEach(md -> sendSystemUserEvent(resolveModuleName(moduleIdNameMap, md), md, tenantName));
  }

  private void sendSystemUserEvent(String moduleName, ModuleDescriptor descriptor, String tenant) {
    var systemUser = descriptor.getUser();
    var moduleId = descriptor.getId();
    var payload = SystemUserEvent.of(moduleName, systemUser.getType(), systemUser.getPermissions());

    var event = ResourceEvent.<SystemUserEvent>builder()
      .type(CREATE)
      .tenant(tenant)
      .resourceName(SYS_USER_RESOURCE_NAME)
      .newValue(payload)
      .build();

    kafkaEventPublisher.send(getTenantTopicName(SYS_USER_TOPIC, tenant), moduleId, event);
  }

  private static boolean hasSystemUser(ModuleDescriptor moduleDescriptor) {
    return moduleDescriptor.getUser() != null;
  }

  private static Map<String, String> getModuleIdNames(ApplicationDescriptor descriptor) {
    return toStream(descriptor.getModules()).collect(toMap(Module::getId, Module::getName));
  }

  private static String resolveModuleName(Map<String, String> moduleIdNameMap, ModuleDescriptor descriptor) {
    return moduleIdNameMap.get(descriptor.getId());
  }
}
