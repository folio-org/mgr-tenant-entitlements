package org.folio.entitlement.integration.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(Include.NON_EMPTY)
@AllArgsConstructor(staticName = "of")
public class CapabilityEventPayload {

  /**
   * Module identifier.
   */
  private String moduleId;

  /**
   * Module type: be or ui.
   */
  private ModuleType moduleType;

  /**
   * Application identifier.
   */
  private String applicationId;

  /**
   * List with defined folio resources and corresponding permissions.
   */
  private List<FolioResource> resources;

  /**
   * Sets moduleId for {@link CapabilityEventPayload} and returns {@link CapabilityEventPayload}.
   *
   * @return this {@link CapabilityEventPayload} with new moduleId value
   */
  public CapabilityEventPayload moduleId(String moduleId) {
    this.moduleId = moduleId;
    return this;
  }

  /**
   * Sets moduleType for {@link CapabilityEventPayload} and returns {@link CapabilityEventPayload}.
   *
   * @return this {@link CapabilityEventPayload} with new moduleType value
   */
  public CapabilityEventPayload moduleType(ModuleType moduleType) {
    this.moduleType = moduleType;
    return this;
  }

  /**
   * Sets applicationId for {@link CapabilityEventPayload} and returns {@link CapabilityEventPayload}.
   *
   * @return this {@link CapabilityEventPayload} with new applicationId value
   */
  public CapabilityEventPayload applicationId(String applicationId) {
    this.applicationId = applicationId;
    return this;
  }

  /**
   * Sets resources for {@link CapabilityEventPayload} and returns {@link CapabilityEventPayload}.
   *
   * @return this {@link CapabilityEventPayload} with new resources value
   */
  public CapabilityEventPayload resources(List<FolioResource> resources) {
    this.resources = resources;
    return this;
  }
}
