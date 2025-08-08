package org.folio.entitlement.service.validator;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.IGNORE_ERRORS;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.commonStageContext;

import java.util.List;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.validator.StageRequestValidator.NoOp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class NoOpStageRequestValidatorTest {

  private static final String VALIDATOR_NAME = "testValidator";

  @ParameterizedTest
  @NullAndEmptySource
  void constructor_negative_invalidName(String name) {
    assertThatThrownBy(() -> new NoOp(name))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Validator name must not be null or blank");
  }

  @Test
  void getName_positive() {
    var validator = new NoOp(VALIDATOR_NAME);
    var result = validator.getName();
    assertThat(result).isEqualTo(VALIDATOR_NAME);
  }

  @Test
  void validate_positive_doesNotThrowException() {
    assertThatNoException().isThrownBy(() ->
      new NoOp(VALIDATOR_NAME).validate(entitlementRequest()));
  }

  @Test
  void shouldValidate_positive_returnsFalse() {
    var result = new NoOp(VALIDATOR_NAME).shouldValidate(entitlementRequest());
    assertThat(result).isFalse();
  }

  @Test
  void execute_positive_doesNotThrowException() {
    assertThatNoException().isThrownBy(() ->
      new NoOp(VALIDATOR_NAME).execute(commonStageContext(FLOW_ID, emptyMap(), emptyMap())));
  }

  private static EntitlementRequest entitlementRequest() {
    return EntitlementRequest.builder()
      .type(ENTITLE)
      .tenantId(TENANT_ID)
      .purge(false)
      .async(false)
      .ignoreErrors(IGNORE_ERRORS)
      .applications(List.of(APPLICATION_ID))
      .okapiToken(OKAPI_TOKEN)
      .build();
  }
}
