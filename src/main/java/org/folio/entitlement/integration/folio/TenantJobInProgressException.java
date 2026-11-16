package org.folio.entitlement.integration.folio;

/**
 * Signals to the retry policy that the tenant job is not complete yet. Stack trace is disabled because one instance
 * is created per poll.
 */
final class TenantJobInProgressException extends RuntimeException {

  TenantJobInProgressException() {
    super("Tenant job is still in progress", null, false, false);
  }
}
