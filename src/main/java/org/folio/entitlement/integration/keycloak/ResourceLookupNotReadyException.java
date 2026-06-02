package org.folio.entitlement.integration.keycloak;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class ResourceLookupNotReadyException extends WebApplicationException {

  public ResourceLookupNotReadyException(String resourceName) {
    super("Keycloak resource not yet visible by name: " + resourceName, Response.Status.SERVICE_UNAVAILABLE);
  }
}
