package org.folio.entitlement.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.UserDescriptor;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.integration.kafka.model.SystemUserEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SystemUserEventProvider {

  private static final String EXTENSIONS_USER_FIELD = "user";
  private final ObjectMapper objectMapper;

  /**
   * Retrieves system {@link UserDescriptor} from {@link ModuleDescriptor}.
   *
   * @param moduleDescriptor - module descriptor to be analyzed
   * @return {@link Optional} of {@link UserDescriptor}
   */
  public Optional<SystemUserEvent> getSystemUserEvent(ModuleDescriptor moduleDescriptor) {
    return findSystemUser(moduleDescriptor)
      .map(systemUser -> getSystemUserEvent(moduleDescriptor, systemUser));
  }

  private static SystemUserEvent getSystemUserEvent(ModuleDescriptor moduleDescriptor, UserDescriptor systemUser) {
    var moduleName = SemverUtils.getName(moduleDescriptor.getId());
    return SystemUserEvent.of(moduleName, systemUser.getType(), systemUser.getPermissions());
  }

  public Optional<UserDescriptor> findSystemUser(ModuleDescriptor moduleDescriptor) {
    if (moduleDescriptor == null) {
      return Optional.empty();
    }

    var metadata = moduleDescriptor.getMetadata();
    if (metadata != null && MapUtils.isNotEmpty(metadata.properties())) {
      var extensionsUserDescriptor = metadata.properties().get(EXTENSIONS_USER_FIELD);
      return Optional.ofNullable(objectMapper.convertValue(extensionsUserDescriptor, UserDescriptor.class));
    }

    //noinspection deprecation
    return Optional.ofNullable(moduleDescriptor.getUser());
  }
}
