package org.folio.entitlement.integration.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@JsonInclude(Include.NON_EMPTY)
@AllArgsConstructor(staticName = "of")
public class CapabilityEventBody {

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
}
