package org.folio.entitlement.service;

import static java.util.stream.Collectors.joining;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.validator.EntitlementRequestValidator;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class EntitlementValidationService {

  private final List<EntitlementRequestValidator> entitlementRequestValidators;

  public EntitlementValidationService(List<EntitlementRequestValidator> validators) {
    this.entitlementRequestValidators = emptyIfNull(validators);
  }

  public void validate(EntitlementRequest request) {
    log.info("Validating entitlement request: {}", request);

    entitlementRequestValidators.stream()
      .filter(validator -> validator.shouldValidate(request))
      .forEach(validator -> {
        log.debug("Validating entitlement request by: {}", validator.getName());
        validator.validate(request);
      });
  }

  public void validateBy(String validatorName, EntitlementRequest request) {
    if (isBlank(validatorName)) {
      throw new RequestValidationException("Validator name is empty", "validator", null);
    }

    log.info("Validating entitlement request by specific validator: validator = {}, request = {}",
      validatorName, request);

    var validator = getValidatorByName(validatorName);
    if (validator.shouldValidate(request)) {
      validator.validate(request);
    } else {
      log.debug("Validator is not designed to verify the request");
    }
  }

  private EntitlementRequestValidator getValidatorByName(String validatorName) {
    return entitlementRequestValidators.stream()
      .filter(validator -> validatorName.equalsIgnoreCase(validator.getName()))
      .findFirst()
      .orElseThrow(() -> new RequestValidationException(
        "Invalid validator name. Should be one of the following: " + validatorNames(), "validator", validatorName));
  }

  private String validatorNames() {
    return entitlementRequestValidators.stream()
      .map(EntitlementRequestValidator::getName)
      .collect(joining(", ", "[", "]"));
  }
}
