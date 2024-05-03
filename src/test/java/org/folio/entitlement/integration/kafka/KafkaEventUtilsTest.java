package org.folio.entitlement.integration.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.integration.kafka.model.ResourceEventType.CREATE;
import static org.folio.entitlement.integration.kafka.model.ResourceEventType.DELETE;
import static org.folio.entitlement.integration.kafka.model.ResourceEventType.UPDATE;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Optional;
import java.util.stream.Stream;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.integration.kafka.model.ResourceEventType;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class KafkaEventUtilsTest {

  @ParameterizedTest
  @DisplayName("getResourceEventType_positive_parameterized")
  @CsvSource({"v2,v1,UPDATE", "v2,,CREATE", ", v1,DELETE", ",,DELETE"})
  void getResourceEventType_positive_parameterized(String newValue, String oldValue, ResourceEventType eventType) {
    var result = KafkaEventUtils.getResourceEventType(newValue, oldValue);
    assertThat(result).isEqualTo(eventType);
  }

  @ParameterizedTest(name = "[{index}] newValue={0}, oldValue={1}")
  @MethodSource("createEventDatasource")
  @DisplayName("getResourceEventType_parameterized")
  void createEvent_positive(String newValue, String oldValue, ResourceEvent<String> event) {
    var resourceName = "test-resource";
    var result = KafkaEventUtils.createEvent(resourceName, TENANT_NAME, newValue, oldValue);
    assertThat(result).isEqualTo(Optional.ofNullable(event));
  }

  private static Stream<Arguments> createEventDatasource() {
    return Stream.of(
      arguments("v2", "v1", resourceEvent(UPDATE, "v2", "v1")),
      arguments("v2", null, resourceEvent(CREATE, "v2", null)),
      arguments(null, "v1", resourceEvent(DELETE, null, "v1")),
      arguments(null, null, null)
    );
  }

  private static ResourceEvent<String> resourceEvent(ResourceEventType eventType, String newValue, String oldValue) {
    return ResourceEvent.<String>builder()
      .tenant(TENANT_NAME)
      .type(eventType)
      .resourceName("test-resource")
      .newValue(newValue)
      .oldValue(oldValue)
      .build();
  }
}
