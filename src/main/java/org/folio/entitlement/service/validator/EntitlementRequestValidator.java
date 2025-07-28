package org.folio.entitlement.service.validator;

import org.folio.entitlement.domain.model.EntitlementRequest;

public interface EntitlementRequestValidator {

  /**
   * Returns name assigned to validator. By default, the name is simple class name of the validator.
   *
   * @return validator name
   */
  default String getName() {
    return getClass().getSimpleName();
  }

  /**
   * Performs {@link EntitlementRequest} validation.
   *
   * @param entitlementRequest - entitlement request to validate.
   * @throws org.folio.entitlement.exception.RequestValidationException if validation is failed
   */
  void validate(EntitlementRequest entitlementRequest);

  /**
   * Checks if validator must be executed or not.
   *
   * @param entitlementRequest - entitlement request to analyze.
   * @return true if entitlement request validator must be executed, false - otherwise
   */
  boolean shouldValidate(EntitlementRequest entitlementRequest);

  class Order {

    public static final int APPLICATION_FLOW = 1;
    public static final int EXISTING_ENTITLEMENT = 2;
    public static final int UPGRADE_REQUEST = 3;
    public static final int INTERFACE_INTEGRITY = 4;
  }
}
