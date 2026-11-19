package org.folio.entitlement.integration.folio;

/**
 * Signals to the retry policy that reading the tenant job failed in a way worth repeating (I/O error or server error).
 * Stack trace is disabled because many instances can be created while a module is unavailable.
 */
final class TenantJobTransientException extends RuntimeException {

  TenantJobTransientException(Throwable cause) {
    super("Transient failure while polling tenant job", cause, false, false);
  }
}
