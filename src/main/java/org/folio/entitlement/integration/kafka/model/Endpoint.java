package org.folio.entitlement.integration.kafka.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor(staticName = "of")
public class Endpoint {

  /**
   * Endpoint path or path pattern.
   */
  private String path;

  /**
   * Http method.
   */
  private String method;
}
