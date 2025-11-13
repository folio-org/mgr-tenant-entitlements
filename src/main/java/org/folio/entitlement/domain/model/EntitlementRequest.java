package org.folio.entitlement.domain.model;

import static org.folio.flow.model.FlowExecutionStrategy.CANCEL_ON_ERROR;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;

import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.flow.model.FlowExecutionStrategy;

@Data
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class EntitlementRequest {

  /**
   * Tenant identifier.
   */
  private final UUID tenantId;

  /**
   * Entitlement request type.
   */
  private final EntitlementRequestType type;

  /**
   * List of applications to install.
   */
  private final List<String> applications;

  /**
   * Tenant parameters that will be passed to each module during module enabling.
   */
  private final String tenantParameters;

  /**
   * X-Okapi-Token value to proxy request to okapi (optional).
   */
  @ToString.Exclude
  private final String okapiToken;

  /**
   * Defines if disabled modules will also be purged.
   */
  private final boolean purge;

  /**
   * Defines if module data must be purged on rollback.
   */
  private final boolean purgeOnRollback;

  /**
   * Defines if errors must be ignored during installation process.
   */
  private final boolean ignoreErrors;

  /**
   * Defines if errors must be ignored during installation process.
   */
  private final boolean async;

  /**
   * Returns {@link FlowExecutionStrategy} value based on ignoreOnError parameter.
   *
   * @return {@link FlowExecutionStrategy} value
   */
  public FlowExecutionStrategy getExecutionStrategy() {
    return ignoreErrors ? IGNORE_ON_ERROR : CANCEL_ON_ERROR;
  }
}
