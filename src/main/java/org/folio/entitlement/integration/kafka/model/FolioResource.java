package org.folio.entitlement.integration.kafka.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.folio.security.domain.model.descriptor.Permission;

@Data
@AllArgsConstructor(staticName = "of")
public class FolioResource {

  /**
   * Folio permission descriptor.
   */
  private Permission permission;

  /**
   * List with linked endpoints.
   */
  private List<Endpoint> endpoints;
}
