package org.folio.entitlement.service.validator;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;

public abstract class StageRequestValidator extends DatabaseLoggingStage<CommonStageContext>
  implements EntitlementRequestValidator {

  /**
   * No-op implementation of {@link StageRequestValidator} that does nothing.
   * This can be used when a validator is not needed or disabled by configuration,
   * but the flow requires a validator to be present in the pipeline.
   */
  public static final class NoOp extends StageRequestValidator {

    private final String name;

    public NoOp(String validatorName) {
      if (isBlank(validatorName)) {
        throw new IllegalArgumentException("Validator name must not be null or blank");
      }
      this.name = validatorName;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public void validate(EntitlementRequest entitlementRequest) {
      // No validation logic, this is a no-op validator
    }

    @Override
    public boolean shouldValidate(EntitlementRequest entitlementRequest) {
      return false;
    }

    @Override
    public void execute(CommonStageContext context) {
      // No execution logic, this is a no-op validator
    }
  }
}
