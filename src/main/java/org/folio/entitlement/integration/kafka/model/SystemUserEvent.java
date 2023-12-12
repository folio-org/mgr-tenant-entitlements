package org.folio.entitlement.integration.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor(staticName = "of")
public class SystemUserEvent {

  private String name;
  private String type;
  private List<String> permissions;
}
