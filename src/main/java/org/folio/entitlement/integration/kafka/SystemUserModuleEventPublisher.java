package org.folio.entitlement.integration.kafka;

import static org.folio.entitlement.integration.kafka.KafkaEventUtils.SYSTEM_USER_RESOURCE_NAME;
import static org.folio.integration.kafka.KafkaUtils.getTenantTopicName;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.integration.kafka.model.SystemUserEvent;
import org.folio.entitlement.utils.SemverUtils;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class SystemUserModuleEventPublisher extends AbstractModuleEventPublisher<SystemUserEvent> {

  @Override
  protected Optional<SystemUserEvent> getEventPayload(String appId, ModuleType type, ModuleDescriptor descriptor) {
    return getSystemUserEvent(descriptor);
  }

  @Override
  protected String getTopicName(String tenantName) {
    return getTenantTopicName(KafkaEventUtils.SYSTEM_USER_TOPIC, tenantName);
  }

  @Override
  protected String getResourceName() {
    return SYSTEM_USER_RESOURCE_NAME;
  }

  /**
   * Creates a system user event for provided {@link ModuleDescriptor} object.
   *
   * @param moduleDescriptor - {@link ModuleDescriptor} object
   * @return {@link Optional} of {@link SystemUserEvent}, it will be empty if descriptor does not contain system user
   */
  public static Optional<SystemUserEvent> getSystemUserEvent(ModuleDescriptor moduleDescriptor) {
    if (moduleDescriptor == null || moduleDescriptor.getUser() == null) {
      return Optional.empty();
    }

    var moduleName = SemverUtils.getName(moduleDescriptor.getId());
    var systemUser = moduleDescriptor.getUser();
    var payload = SystemUserEvent.of(moduleName, systemUser.getType(), systemUser.getPermissions());
    return Optional.of(payload);
  }
}
