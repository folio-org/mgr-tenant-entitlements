package org.folio.entitlement.integration.folio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TenantJob {

  private String id;
  private String tenant;
  private Boolean complete;
  private String error;
  private List<String> messages;
}
