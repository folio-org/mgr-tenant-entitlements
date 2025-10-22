package org.folio.entitlement.integration.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.kafka.model.PermissionMappingValue;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.integration.kafka.model.ResourceEventType;

@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class KafkaEventUtils {

  public static final String TOPIC_TENANT_COLLECTION_KEY = "ALL";

  public static final String TOPIC_PREFIX = "mgr-tenant-entitlements.";
  public static final String SCHEDULED_JOB_TOPIC = TOPIC_PREFIX + "scheduled-job";
  public static final String CAPABILITIES_TOPIC = TOPIC_PREFIX + "capability";
  public static final String SYSTEM_USER_TOPIC = TOPIC_PREFIX + "system-user";

  public static final String CAPABILITY_RESOURCE_NAME = "Capability";
  public static final String SYSTEM_USER_RESOURCE_NAME = "System user";
  public static final String SCHEDULED_JOB_RESOURCE_NAME = "Scheduled Job";

  private static Map<String, PermissionMappingValue> permissionMapping = Collections.emptyMap();

  /**
   * Creates {@link ResourceEvent} object for given tenant nane, new and old event bodies.
   *
   * @param resource - resource name as {@link String}
   * @param tenant - tenant name as {@link String}
   * @param newPayload - new value in {@link ResourceEvent}
   * @param oldPayload - old value in {@link ResourceEvent}
   * @param <T> generic type for event payload
   * @return {@link Optional} of {@link ResourceEvent}, it will be empty if old and new values are not valid
   */
  public static <T> Optional<ResourceEvent<T>> createEvent(String resource, String tenant, T newPayload, T oldPayload) {
    if (newPayload == null && oldPayload == null) {
      return Optional.empty();
    }

    var scheduledJobEvent = ResourceEvent.<T>builder()
      .tenant(tenant)
      .type(getResourceEventType(newPayload, oldPayload))
      .resourceName(resource)
      .newValue(newPayload)
      .oldValue(oldPayload)
      .build();

    return Optional.of(scheduledJobEvent);
  }

  /**
   * Generates {@link ResourceEventType} value for old and new values.
   *
   * @param newValue - new value for {@link org.folio.entitlement.integration.kafka.model.ResourceEvent}
   * @param oldValue - previous (old) value for {@link org.folio.entitlement.integration.kafka.model.ResourceEvent}
   * @return {@link ResourceEventType} for {@link org.folio.entitlement.integration.kafka.model.ResourceEvent}
   */
  public static ResourceEventType getResourceEventType(Object newValue, Object oldValue) {
    if (newValue != null && oldValue == null) {
      return ResourceEventType.CREATE;
    }

    return newValue != null ? ResourceEventType.UPDATE : ResourceEventType.DELETE;
  }

  //  load permission mappings from a JSON file
  static {
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      InputStream mappingFileAsStream = KafkaEventUtils.class.getClassLoader()
        .getResourceAsStream("permissionmappings/mapping.json");
      permissionMapping = objectMapper.readValue(
        mappingFileAsStream, new TypeReference<>() {
        });
    } catch (IOException e) {
      log.error("Can't initialize Permission mapping", e);
    }
  }

  public static PermissionMappingValue getPermissionValueMapping(String key) {
    return permissionMapping.get(key);
  }

  public static boolean isPermissionMappingExist(String key) {
    return permissionMapping.containsKey(key);
  }

  public static Map<String, PermissionMappingValue> getPermissionMapping() {
    return permissionMapping;
  }
}
