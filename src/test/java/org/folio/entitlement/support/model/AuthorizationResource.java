package org.folio.entitlement.support.model;

import java.util.Arrays;
import java.util.List;

public record AuthorizationResource(String name, List<String> scope) {

  public static AuthorizationResource of(String name, String... scopes) {
    return new AuthorizationResource(name, Arrays.asList(scopes));
  }
}
