package org.folio.entitlement.exception;

import lombok.Getter;

/**
 * Thrown when an entitlement operation cannot proceed because a conflicting one is in progress: a tenant-scoped
 * request or another platform-scoped request while a platform-scoped operation runs, or a platform-scoped request while
 * a tenant-scoped flow is not finished. Mapped to {@code 409 Conflict} with a {@code Retry-After} header; the caller is
 * responsible for retrying.
 */
@Getter
public class EntitlementFlowInProgressException extends RuntimeException {

  private static final long serialVersionUID = -4182715406953127385L;

  /**
   * Interval, in seconds, after which the caller may retry.
   */
  private final int retryAfterSeconds;

  public EntitlementFlowInProgressException(String message, int retryAfterSeconds) {
    super(message);
    this.retryAfterSeconds = retryAfterSeconds;
  }
}
