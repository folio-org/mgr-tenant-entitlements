package org.folio.entitlement.integration.kong.model.operator;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BoolOperator implements RouteOperator {

  AND("&&"),
  OR("||");

  private final String stringValue;
}
