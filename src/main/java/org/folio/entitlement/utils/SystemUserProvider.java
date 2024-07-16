package org.folio.entitlement.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.UserDescriptor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SystemUserProvider {

  private static final String EXTENSIONS_USER_FIELD = "user";
  private final ObjectMapper objectMapper;

  /**
   * Retrieves system {@link UserDescriptor} from {@link ModuleDescriptor}.
   *
   * @param moduleDescriptor - module descriptor to be analyzed
   * @return {@link Optional} of {@link UserDescriptor}
   */
  public Optional<UserDescriptor> findSystemUserDescriptor(ModuleDescriptor moduleDescriptor) {
    if (moduleDescriptor == null) {
      return Optional.empty();
    }

    var extensions = moduleDescriptor.getExtensions();
    if (extensions != null && MapUtils.isNotEmpty(extensions.properties())) {
      var extensionsUserDescriptor = extensions.properties().get(EXTENSIONS_USER_FIELD);
      return Optional.ofNullable(objectMapper.convertValue(extensionsUserDescriptor, UserDescriptor.class));
    }

    //noinspection deprecation
    return Optional.ofNullable(moduleDescriptor.getUser());
  }
}
