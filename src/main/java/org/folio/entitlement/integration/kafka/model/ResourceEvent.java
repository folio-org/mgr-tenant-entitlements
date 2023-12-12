package org.folio.entitlement.integration.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(Include.NON_EMPTY)
public class ResourceEvent<T> {

  /**
   * Resource identifier.
   */
  private String id;

  /**
   * Event type.
   */
  private ResourceEventType type;

  /**
   * Tenant identifier (name).
   */
  private String tenant;

  /**
   * Name of resource.
   */
  private String resourceName;

  /**
   * New value (if resource is created or updated).
   */
  @JsonProperty("new")
  private T newValue;

  /**
   * Previous version value (if resource was updated or deleted).
   */
  @JsonProperty("old")
  private T oldValue;
}
