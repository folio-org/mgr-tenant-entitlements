package org.folio.entitlement.exception;

import lombok.Getter;

/**
 * Thrown when a deployment-wide platform operation holds the platform lock, so the request cannot proceed. Mapped to
 * {@code 409 Conflict} with a {@code Retry-After} header; the caller is responsible for retrying.
 */
@Getter
public class PlatformOperationInProgressException extends RuntimeException {

  private static final long serialVersionUID = 6025163577204311407L;

  /**
   * Interval, in seconds, after which the caller may retry.
   */
  private final int retryAfterSeconds;

  public PlatformOperationInProgressException(String message, int retryAfterSeconds) {
    super(message);
    this.retryAfterSeconds = retryAfterSeconds;
  }
}
