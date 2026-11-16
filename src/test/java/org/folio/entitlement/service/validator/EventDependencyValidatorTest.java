package org.folio.entitlement.service.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_APP_DESCRIPTORS;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.common.domain.model.ConsumesEvent;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.ProducesEvent;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.validator.adp.ApplicationDescriptorProvider;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EventDependencyValidatorTest {

  private static final String CONSUMER_ID = "consumer-1.0.0";

  @Mock private ApplicationDescriptorProvider provider;

  @Test
  void validate_positive_matchedEvent() {
    var request = request(CONSUMER_ID);
    when(provider.getDescriptors(request)).thenReturn(List.of(
      app("producer-1.0.0", produces("e.a")), app(CONSUMER_ID, consumes("e.a", false))));

    var validator = validator(true);
    assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
  }

  @Test
  void validate_positive_optionalWithoutProducerNeverFails() {
    var request = request(CONSUMER_ID);
    when(provider.getDescriptors(request)).thenReturn(List.of(app(CONSUMER_ID, consumes("e.a", true))));

    var validator = validator(true);
    assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
  }

  @Test
  void validate_positive_requiredWithoutProducerFlagOff() {
    var request = request(CONSUMER_ID);
    when(provider.getDescriptors(request)).thenReturn(List.of(app(CONSUMER_ID, consumes("e.a", false))));

    var validator = validator(false);
    assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
  }

  @Test
  void validate_negative_requiredWithoutProducerFlagOn() {
    var request = request(CONSUMER_ID);
    when(provider.getDescriptors(request)).thenReturn(List.of(app(CONSUMER_ID, consumes("e.a", false))));

    var validator = validator(true);
    assertThatThrownBy(() -> validator.validate(request))
      .hasMessageContaining("Consumed events without producers found: e.a")
      .isInstanceOfSatisfying(RequestValidationException.class, e ->
        assertThat(e.getErrorParameters()).anySatisfy(p -> {
          assertThat(p.getKey()).isEqualTo("e.a");
          assertThat(p.getValue()).isEqualTo(CONSUMER_ID);
        }));
  }

  @Test
  void execute_positive_matchedEvent() {
    var consumer = app(CONSUMER_ID, consumes("e.a", false));
    var context = stageContext(consumer);
    when(provider.getDescriptors(context)).thenReturn(List.of(app("producer-1.0.0", produces("e.a")), consumer));

    var validator = validator(true);
    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_negative_requiredWithoutProducerFlagOn() {
    var consumer = app(CONSUMER_ID, consumes("e.a", false));
    var context = stageContext(consumer);
    when(provider.getDescriptors(context)).thenReturn(List.of(consumer));

    var validator = validator(true);
    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessageContaining("Consumed events without producers found: e.a");
  }

  @Test
  void execute_positive_requiredWithoutProducerFlagOff() {
    var consumer = app(CONSUMER_ID, consumes("e.a", false));
    var context = stageContext(consumer);
    when(provider.getDescriptors(context)).thenReturn(List.of(consumer));

    var validator = validator(false);
    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_providerNotCalledWhenRequestedAppsConsumeNothing() {
    var context = stageContext(app(CONSUMER_ID, produces("e.a")), app("plain-1.0.0", new ModuleDescriptor()));

    var validator = validator(true);
    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
    verifyNoInteractions(provider);
  }

  @Test
  void execute_positive_noRequestedDescriptorsInContext() {
    var context = commonStageContext(FLOW_ID, Map.of(PARAM_REQUEST, request(CONSUMER_ID)), Map.of());

    var validator = validator(true);
    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
    verifyNoInteractions(provider);
  }

  @Test
  void validate_positive_consumersOutsideTheRequestAreNotChecked() {
    var request = request(CONSUMER_ID);
    when(provider.getDescriptors(request)).thenReturn(List.of(
      app(CONSUMER_ID, consumes("e.a", false)),
      app("producer-1.0.0", produces("e.a")),
      app("entitled-1.0.0", consumes("e.unproduced", false))));

    var validator = validator(true);
    assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
  }

  @Test
  void validate_positive_producerWithoutConsumer() {
    var request = request(CONSUMER_ID);
    when(provider.getDescriptors(request)).thenReturn(List.of(
      app("producer-1.0.0", produces("e.a")), app(CONSUMER_ID, consumes("e.b", true))));

    var validator = validator(true);
    assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
  }

  @Test
  void validate_positive_noEventDeclarations() {
    var request = request(CONSUMER_ID);
    when(provider.getDescriptors(request)).thenReturn(List.of(
      app("plain-1.0.0", new ModuleDescriptor()), app(CONSUMER_ID, new ModuleDescriptor())));

    var validator = validator(true);
    assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @NullAndEmptySource
  void validate_positive_noDescriptors(List<ApplicationDescriptor> descriptors) {
    var request = request(CONSUMER_ID);
    when(provider.getDescriptors(request)).thenReturn(descriptors);

    var validator = validator(true);
    assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @CsvSource({
    "ENTITLE, ENTITLE, true",
    "STATE, UPGRADE, false",
    "UPGRADE, REVOKE, false"
  })
  void shouldValidate_onlyForConfiguredType(EntitlementRequestType validatorType, EntitlementRequestType requestType,
    boolean expected) {
    var validator = new EventDependencyValidator(validatorType, provider, false);
    var request = EntitlementRequest.builder().type(requestType).build();

    assertThat(validator.shouldValidate(request)).isEqualTo(expected);
  }

  private EventDependencyValidator validator(boolean strict) {
    return new EventDependencyValidator(EntitlementRequestType.ENTITLE, provider, strict);
  }

  private static CommonStageContext stageContext(ApplicationDescriptor... requestedApps) {
    var request = request(Stream.of(requestedApps).map(ApplicationDescriptor::getId).toArray(String[]::new));
    return commonStageContext(FLOW_ID, Map.of(PARAM_REQUEST, request),
      Map.of(PARAM_APP_DESCRIPTORS, List.of(requestedApps)));
  }

  private static EntitlementRequest request(String... applicationIds) {
    return EntitlementRequest.builder().applications(List.of(applicationIds)).build();
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
