package org.folio.entitlement.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.support.TestUtils.OBJECT_MAPPER;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.UserDescriptor;
import org.folio.entitlement.integration.kafka.model.SystemUserEvent;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SystemUserEventProviderTest {

  @InjectMocks private SystemUserEventProvider systemUserEventProvider;
  @Spy private final ObjectMapper objectMapper = OBJECT_MAPPER;

  @Test
  @Deprecated
  void getSystemUserEvent_positive_moduleDescriptorWithoutSystemUser() {
    var moduleDescriptor = new ModuleDescriptor();

    var result = systemUserEventProvider.getSystemUserEvent(moduleDescriptor);
    assertThat(result).isEmpty();
    verifyNoInteractions(objectMapper);
  }

  @Test
  @Deprecated
  void getSystemUserEvent_positive_userSection() {
    var userDescriptor = UserDescriptor.of("module", List.of("test.permission"));
    var moduleDescriptor = new ModuleDescriptor().id("test-module-0.0.1").user(userDescriptor);

    var result = systemUserEventProvider.getSystemUserEvent(moduleDescriptor);
    assertThat(result).contains(SystemUserEvent.of("test-module", "module", List.of("test.permission")));
    verifyNoInteractions(objectMapper);
  }

  @Test
  void getSystemUserEvent_positive_metadataSection() throws JsonProcessingException {
    var moduleDescriptorJson = """
      {
        "id": "test-module-0.0.1",
        "name": "Test Module",
        "metadata": {
          "user": { "type": "system", "permissions": [ "test.permission" ] }
        }
      }""";

    var moduleDescriptor = OBJECT_MAPPER.readValue(moduleDescriptorJson, ModuleDescriptor.class);
    var result = systemUserEventProvider.getSystemUserEvent(moduleDescriptor);

    assertThat(result).contains(SystemUserEvent.of("test-module", "system", List.of("test.permission")));
    verify(objectMapper).convertValue(anyMap(), eq(UserDescriptor.class));
  }

  @Test
  void getSystemUserEvent_positive_metadataIsNull() {
    var moduleDescriptor = new ModuleDescriptor().id("test-module-0.0.1").metadata(null);

    var result = systemUserEventProvider.getSystemUserEvent(moduleDescriptor);
    assertThat(result).isEmpty();
  }

  @Test
  void getSystemUserEvent_positive_unknownMetadataKey() throws JsonProcessingException {
    var moduleDescriptorJson = """
      {
        "id": "test-module-0.0.1",
        "name": "Test Module",
        "metadata": { "unknown": { "key": "value" } }
      }""";

    var moduleDescriptor = OBJECT_MAPPER.readValue(moduleDescriptorJson, ModuleDescriptor.class);
    var result = systemUserEventProvider.getSystemUserEvent(moduleDescriptor);
    assertThat(result).isEmpty();
    verify(objectMapper).convertValue(eq(null), eq(UserDescriptor.class));
  }
}
