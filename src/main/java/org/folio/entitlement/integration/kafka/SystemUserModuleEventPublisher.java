package org.folio.entitlement.integration.kafka;

import static org.folio.entitlement.integration.kafka.KafkaEventUtils.SYSTEM_USER_RESOURCE_NAME;
import static org.folio.integration.kafka.KafkaUtils.getTenantTopicName;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.UserDescriptor;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.integration.kafka.model.SystemUserEvent;
import org.folio.entitlement.utils.SystemUserProvider;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class SystemUserModuleEventPublisher extends AbstractModuleEventPublisher<SystemUserEvent> {

  private final SystemUserProvider systemUserProvider;

  @Override
  protected Optional<SystemUserEvent> getEventPayload(String appId, ModuleType type, ModuleDescriptor descriptor) {
    return systemUserProvider.findSystemUserDescriptor(descriptor)
      .map(systemUser -> getSystemUserEvent(descriptor, systemUser));
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
   * @param systemUser - {@link ModuleDescriptor} object
   * @return {@link Optional} of {@link SystemUserEvent}, it will be empty if descriptor does not contain system user
   */
  public static SystemUserEvent getSystemUserEvent(ModuleDescriptor moduleDescriptor, UserDescriptor systemUser) {
    var moduleName = SemverUtils.getName(moduleDescriptor.getId());
    return SystemUserEvent.of(moduleName, systemUser.getType(), systemUser.getPermissions());
  }
}
