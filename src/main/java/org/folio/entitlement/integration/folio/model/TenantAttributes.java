package org.folio.entitlement.integration.folio.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import org.folio.common.domain.model.error.Parameter;

@Data
public class TenantAttributes {

  /**
   * Existing module ID. If omitted, the module is not enabled already.
   */
  @JsonProperty("module_from")
  private String moduleFrom;

  /**
   * Existing module ID. If omitted, the module is not enabled already.
   */
  @JsonProperty("module_to")
  private String moduleTo;

  /**
   * On disable should data also be purged.
   */
  private Boolean purge;

  /**
   * List of key/value parameters.
   */
  private List<Parameter> parameters;
}
