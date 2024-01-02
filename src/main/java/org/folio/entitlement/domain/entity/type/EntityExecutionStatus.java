package org.folio.entitlement.domain.entity.type;

import org.folio.entitlement.domain.dto.ExecutionStatus;

public enum EntityExecutionStatus {

  QUEUED,
  IN_PROGRESS,
  CANCELLED,
  CANCELLATION_FAILED,
  FAILED,
  FINISHED;

  /**
   * Creates {@link EntityExecutionStatus} from {@link ExecutionStatus} enum value.
   *
   * @param status - {@link ExecutionStatus} to process
   * @return {@link EntityExecutionStatus} from {@link ExecutionStatus}
   */
  public static EntityExecutionStatus from(ExecutionStatus status) {
    return EntityExecutionStatus.valueOf(status.name());
  }
}
