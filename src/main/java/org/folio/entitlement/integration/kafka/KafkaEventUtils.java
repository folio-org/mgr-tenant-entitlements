package org.folio.entitlement.integration.kafka;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.entitlement.integration.kafka.model.ResourceEventType;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class KafkaEventUtils {

  /**
   * Generates {@link ResourceEventType} value for old and new values.
   *
   * @param newValue - new value for {@link org.folio.entitlement.integration.kafka.model.ResourceEvent}
   * @param oldValue - previous (old) value for {@link org.folio.entitlement.integration.kafka.model.ResourceEvent}
   * @return {@link ResourceEventType} for {@link org.folio.entitlement.integration.kafka.model.ResourceEvent}
   */
  public static ResourceEventType getResourceEventType(Object newValue, Object oldValue) {
    return newValue != null && oldValue == null ? ResourceEventType.CREATE : ResourceEventType.UPDATE;
  }
}
