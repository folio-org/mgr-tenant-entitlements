package org.folio.entitlement.service.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.common.domain.model.ConsumesEvent;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.ProducesEvent;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.validator.adp.ApplicationAndDependencyDescriptorProvider;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EventDependencyValidatorTest {

  @Mock private ApplicationAndDependencyDescriptorProvider provider;

  @Test
  void validate_positive_matchedEvent() {
    var request = EntitlementRequest.builder().build();
    when(provider.getDescriptors(request)).thenReturn(List.of(
      app("producer-1.0.0", produces("e.a")), app("consumer-1.0.0", consumes("e.a", false))));

    assertThatCode(() -> validator(true).validate(request)).doesNotThrowAnyException();
  }

  @Test
  void validate_positive_platformProducerMatched() {
    var request = EntitlementRequest.builder().build();
    when(provider.getDescriptors(request)).thenReturn(List.of(
      app("consumer-1.0.0", consumes("e.a", false)), app("app-mgr-services-1.0.0", produces("e.a"))));

    assertThatCode(() -> validator(true).validate(request)).doesNotThrowAnyException();
  }

  @Test
  void validate_positive_optionalWithoutProducerNeverFails() {
    var request = EntitlementRequest.builder().build();
    when(provider.getDescriptors(request)).thenReturn(List.of(app("c-1.0.0", consumes("e.a", true))));

    assertThatCode(() -> validator(true).validate(request)).doesNotThrowAnyException();
  }

  @Test
  void validate_positive_requiredWithoutProducerFlagOff() {
    var request = EntitlementRequest.builder().build();
    when(provider.getDescriptors(request)).thenReturn(List.of(app("c-1.0.0", consumes("e.a", false))));

    assertThatCode(() -> validator(false).validate(request)).doesNotThrowAnyException();
  }

  @Test
  void validate_negative_requiredWithoutProducerFlagOn() {
    var request = EntitlementRequest.builder().build();
    when(provider.getDescriptors(request)).thenReturn(List.of(app("c-1.0.0", consumes("e.a", false))));

    assertThatThrownBy(() -> validator(true).validate(request))
      .hasMessageContaining("Consumed events without producers found: e.a")
      .isInstanceOfSatisfying(RequestValidationException.class, e ->
        assertThat(e.getErrorParameters()).anySatisfy(p -> {
          assertThat(p.getKey()).isEqualTo("e.a");
          assertThat(p.getValue()).isEqualTo("c-1.0.0");
        }));
  }

  @Test
  void validate_positive_producerWithoutConsumerAndNoDeclarations() {
    var request = EntitlementRequest.builder().build();
    when(provider.getDescriptors(request)).thenReturn(List.of(
      app("p-1.0.0", produces("e.a")), app("plain-1.0.0", new ModuleDescriptor())));

    assertThatCode(() -> validator(true).validate(request)).doesNotThrowAnyException();
  }

  @Test
  void validate_positive_emptyDescriptors() {
    var request = EntitlementRequest.builder().build();
    when(provider.getDescriptors(request)).thenReturn(List.of());

    assertThatCode(() -> validator(true).validate(request)).doesNotThrowAnyException();
  }

  @Test
  void validate_positive_nullDescriptors() {
    var request = EntitlementRequest.builder().build();
    when(provider.getDescriptors(request)).thenReturn(null);

    assertThatCode(() -> validator(true).validate(request)).doesNotThrowAnyException();
  }

  @Test
  void shouldValidate_onlyForEntitle() {
    var request = EntitlementRequest.builder().type(EntitlementRequestType.ENTITLE).build();
    assertThat(validator(false).shouldValidate(request)).isTrue();
    var revoke = EntitlementRequest.builder().type(EntitlementRequestType.REVOKE).build();
    assertThat(validator(false).shouldValidate(revoke)).isFalse();
  }

  private EventDependencyValidator validator(boolean strict) {
    return new EventDependencyValidator(provider, strict);
  }

  private static ApplicationDescriptor app(String id, ModuleDescriptor module) {
    return new ApplicationDescriptor().id(id).moduleDescriptors(List.of(module));
  }

  private static ModuleDescriptor produces(String event) {
    return new ModuleDescriptor().producesEvents(List.of(new ProducesEvent().event(event)));
  }

  private static ModuleDescriptor consumes(String event, boolean optional) {
    return new ModuleDescriptor().consumesEvents(List.of(new ConsumesEvent().event(event).optional(optional)));
  }
}
