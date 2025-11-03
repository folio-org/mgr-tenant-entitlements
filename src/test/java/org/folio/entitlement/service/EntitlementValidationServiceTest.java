package org.folio.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.validator.EntitlementRequestValidator;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EntitlementValidationServiceTest {

  private EntitlementValidationService service;
  private final EntitlementRequest request = entitlementRequest();

  private static EntitlementRequest entitlementRequest() {
    return EntitlementRequest.builder()
      .tenantId(TENANT_ID)
      .applications(List.of(APPLICATION_ID))
      .type(ENTITLE)
      .build();
  }

  @Nested
  class NoValidators {

    @BeforeEach
    void setUp() {
      service = new EntitlementValidationService(null);
    }

    @Test
    void validate_positive() {
      service.validate(request);
    }

    @Test
    void validateBy_negative_emptyValidatorName() {
      assertThatThrownBy(() -> service.validateBy("", request))
        .isInstanceOf(RequestValidationException.class)
        .hasMessage("Validator name is empty");
    }

    @Test
    void validateBy_negative_invalidValidatorName() {
      var name = "invalid";

      assertThatThrownBy(() -> service.validateBy(name, request))
        .isInstanceOf(RequestValidationException.class)
        .hasMessageContaining("Invalid validator name")
        .satisfies(throwable -> {
          var exc = (RequestValidationException) throwable;

          assertThat(exc.getErrorParameters()).containsExactly(
            new Parameter().key("validator").value(name));
        });
    }
  }

  @Nested
  class WithValidators {

    private static final String FIRST_VALIDATOR = "firstValidator";
    private static final String SECOND_VALIDATOR = "secondValidator";

    @Mock private EntitlementRequestValidator first;
    @Mock private EntitlementRequestValidator second;

    @BeforeEach
    void setUp() {
      lenient().when(first.getName()).thenReturn(FIRST_VALIDATOR);
      lenient().when(second.getName()).thenReturn(SECOND_VALIDATOR);

      service = new EntitlementValidationService(List.of(first, second));
    }

    @Test
    void validate_positive_bothValidatorsCalled() {
      when(first.shouldValidate(request)).thenReturn(Boolean.TRUE);
      when(second.shouldValidate(request)).thenReturn(Boolean.TRUE);
      InOrder inOrder = inOrder(first, second);

      service.validate(request);

      inOrder.verify(first).validate(request);
      inOrder.verify(second).validate(request);
    }

    @Test
    void validate_positive_oneValidatorCalled() {
      when(first.shouldValidate(request)).thenReturn(Boolean.FALSE);
      when(second.shouldValidate(request)).thenReturn(Boolean.TRUE);

      service.validate(request);

      verify(first, never()).validate(request);
      verify(second).validate(request);
    }

    @Test
    void validate_negative_validationException() {
      when(first.shouldValidate(request)).thenReturn(Boolean.TRUE);
      when(second.shouldValidate(request)).thenReturn(Boolean.TRUE);

      var rve = new RequestValidationException("Invalid", "key", "value");
      doThrow(rve).when(second).validate(request);

      InOrder inOrder = inOrder(first, second);

      assertThatThrownBy(() -> service.validate(request))
        .isInstanceOf(RequestValidationException.class)
        .hasMessage(rve.getMessage());

      inOrder.verify(first).validate(request);
      inOrder.verify(second).validate(request);
    }

    @Test
    void validateBy_positive_firstCalled() {
      when(first.shouldValidate(request)).thenReturn(Boolean.TRUE);

      service.validateBy(FIRST_VALIDATOR, request);

      verify(first).validate(request);
      verify(second, never()).validate(request);
    }

    @Test
    void validateBy_positive_secondCalled() {
      when(second.shouldValidate(request)).thenReturn(Boolean.TRUE);

      service.validateBy(SECOND_VALIDATOR, request);

      verify(first, never()).validate(request);
      verify(second).validate(request);
    }

    @Test
    void validateBy_positive_firstShouldNotValidate() {
      when(first.shouldValidate(request)).thenReturn(Boolean.FALSE);

      service.validateBy(FIRST_VALIDATOR, request);

      verify(first, never()).validate(request);
    }

    @Test
    void validateBy_positive_secondShouldNotValidate() {
      when(second.shouldValidate(request)).thenReturn(Boolean.FALSE);

      service.validateBy(SECOND_VALIDATOR, request);

      verify(second, never()).validate(request);
    }

    @Test
    void validateBy_negative_emptyValidatorName() {
      assertThatThrownBy(() -> service.validateBy("", request))
        .isInstanceOf(RequestValidationException.class)
        .hasMessage("Validator name is empty");
    }

    @Test
    void validateBy_negative_invalidValidatorName() {
      var name = "invalid";

      assertThatThrownBy(() -> service.validateBy(name, request))
        .isInstanceOf(RequestValidationException.class)
        .hasMessageContaining("Invalid validator name")
        .satisfies(throwable -> {
          var exc = (RequestValidationException) throwable;

          assertThat(exc.getErrorParameters()).containsExactly(
            new Parameter().key("validator").value(name));
        });
    }
  }
}
