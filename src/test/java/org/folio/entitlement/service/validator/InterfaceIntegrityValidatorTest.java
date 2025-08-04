package org.folio.entitlement.service.validator;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_APP_DESCRIPTORS;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.appDescriptor;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.folio.entitlement.support.TestValues.itfItem;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.validator.adp.ApplicationDescriptorProvider;
import org.folio.entitlement.service.validator.icollector.ApplicationInterfaceCollector;
import org.folio.entitlement.service.validator.icollector.ApplicationInterfaceCollector.RequiredProvidedInterfaces;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
class InterfaceIntegrityValidatorTest {

  private static final String APP_FOO_ID = "app-foo-1.0.0";
  private static final String APP_BAR_ID = "app-bar-1.0.0";

  private InterfaceIntegrityValidator interfaceIntegrityValidator;
  @Mock private ApplicationInterfaceCollector interfaceCollector;
  @Mock private ApplicationDescriptorProvider applicationDescriptorProvider;

  @BeforeEach
  void setUp() {
    interfaceIntegrityValidator = new InterfaceIntegrityValidator(EntitlementType.ENTITLE, interfaceCollector,
      applicationDescriptorProvider);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(interfaceCollector, applicationDescriptorProvider);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("requiredProvidedInterfaces_positive")
  void execute_positive(@SuppressWarnings("unused") String testName,
    RequiredProvidedInterfaces requiredProvidedInterfaces) {
    var applicationDescriptors = List.of(appDescriptor(APP_FOO_ID), appDescriptor(APP_BAR_ID));
    var stageContext = stageContext(applicationDescriptors, entitlementRequest(APP_BAR_ID));

    when(applicationDescriptorProvider.getDescriptors(stageContext)).thenReturn(applicationDescriptors);
    when(interfaceCollector.collectRequiredAndProvided(applicationDescriptors, TENANT_ID))
      .thenReturn(Stream.of(requiredProvidedInterfaces));

    interfaceIntegrityValidator.execute(stageContext);
  }

  @ParameterizedTest
  @MethodSource("requiredProvidedInterfaces_negative")
  void execute_negative_missingInterfaces(RequiredProvidedInterfaces requiredProvidedInterfaces,
    List<Parameter> parameters) {
    var applicationDescriptors = List.of(appDescriptor(APP_FOO_ID), appDescriptor(APP_BAR_ID));
    var stageContext = stageContext(applicationDescriptors, entitlementRequest(APP_FOO_ID, APP_BAR_ID));

    when(applicationDescriptorProvider.getDescriptors(stageContext)).thenReturn(applicationDescriptors);
    when(interfaceCollector.collectRequiredAndProvided(applicationDescriptors, TENANT_ID))
      .thenReturn(Stream.of(requiredProvidedInterfaces));

    assertThatThrownBy(() -> interfaceIntegrityValidator.execute(stageContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Missing interfaces found for the applications")
      .satisfies(error ->
        assertThat(((RequestValidationException) error).getErrorParameters())
          .containsExactlyInAnyOrderElementsOf(parameters));
  }

  @Test
  void execute_negative_noDescriptorsProvided() {
    var applicationDescriptors = List.of(appDescriptor(APP_FOO_ID), appDescriptor(APP_BAR_ID));
    var stageContext = stageContext(applicationDescriptors, entitlementRequest(APP_FOO_ID, APP_BAR_ID));

    when(applicationDescriptorProvider.getDescriptors(stageContext)).thenReturn(emptyList());

    assertThatThrownBy(() -> interfaceIntegrityValidator.execute(stageContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("No application descriptors provided")
      .satisfies(error ->
        assertThat(((RequestValidationException) error).getErrorParameters())
          .containsExactly(new Parameter().key("descriptors").value(null)));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("requiredProvidedInterfaces_positive")
  void validate_positive(@SuppressWarnings("unused") String testName,
    RequiredProvidedInterfaces requiredProvidedInterfaces) {
    var applicationDescriptors = List.of(appDescriptor(APP_FOO_ID), appDescriptor(APP_BAR_ID));
    var entitlementRequest = entitlementRequest(APP_BAR_ID);

    when(applicationDescriptorProvider.getDescriptors(entitlementRequest)).thenReturn(applicationDescriptors);
    when(interfaceCollector.collectRequiredAndProvided(applicationDescriptors, TENANT_ID))
      .thenReturn(Stream.of(requiredProvidedInterfaces));

    interfaceIntegrityValidator.validate(entitlementRequest);
  }

  @ParameterizedTest
  @MethodSource("requiredProvidedInterfaces_negative")
  void validate_negative_missingInterfaces(RequiredProvidedInterfaces requiredProvidedInterfaces,
    List<Parameter> parameters) {
    var applicationDescriptors = List.of(appDescriptor(APP_FOO_ID), appDescriptor(APP_BAR_ID));
    var entitlementRequest = entitlementRequest(APP_BAR_ID);

    when(applicationDescriptorProvider.getDescriptors(entitlementRequest)).thenReturn(applicationDescriptors);
    when(interfaceCollector.collectRequiredAndProvided(applicationDescriptors, TENANT_ID))
      .thenReturn(Stream.of(requiredProvidedInterfaces));

    assertThatThrownBy(() -> interfaceIntegrityValidator.validate(entitlementRequest))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Missing interfaces found for the applications")
      .satisfies(error ->
        assertThat(((RequestValidationException) error).getErrorParameters())
          .containsExactlyInAnyOrderElementsOf(parameters));
  }

  @Test
  void validate_negative_noDescriptorsProvided() {
    var applicationDescriptors = List.of(appDescriptor(APP_FOO_ID), appDescriptor(APP_BAR_ID));
    var entitlementRequest = entitlementRequest(APP_BAR_ID);

    when(applicationDescriptorProvider.getDescriptors(entitlementRequest)).thenReturn(emptyList());

    assertThatThrownBy(() -> interfaceIntegrityValidator.validate(entitlementRequest))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("No application descriptors provided")
      .satisfies(error ->
        assertThat(((RequestValidationException) error).getErrorParameters())
          .containsExactly(new Parameter().key("descriptors").value(null)));
  }

  @ParameterizedTest
  @DisplayName("shouldValidate_parameterized")
  @CsvSource({"ENTITLE,true", "REVOKE,false", "UPGRADE,false", ",false"})
  void shouldValidate_parameterized(EntitlementType type, boolean expected) {
    var request = EntitlementRequest.builder().type(type).build();
    var result = interfaceIntegrityValidator.shouldValidate(request);
    assertThat(result).isEqualTo(expected);
  }

  private static Stream<Arguments> requiredProvidedInterfaces_positive() {
    return Stream.of(
      arguments("no missing interfaces", new RequiredProvidedInterfaces(
        Set.of(itfItem("foo", "1.0", APP_BAR_ID)),
        Map.of(
          "foo", Set.of(itfItem("foo", "1.0", APP_FOO_ID)),
          "bar", Set.of(itfItem("bar", "1.0", APP_BAR_ID))
        ))),
      arguments("no required interfaces", new RequiredProvidedInterfaces(
        emptySet(),
        Map.of(
          "foo", Set.of(itfItem("foo", "1.0", APP_FOO_ID)),
          "bar", Set.of(itfItem("bar", "1.0", APP_BAR_ID))
        )))
    );
  }

  public static Stream<Arguments> requiredProvidedInterfaces_negative() {
    return Stream.of(
      arguments(new RequiredProvidedInterfaces(
        Set.of(
          itfItem("baz", "1.0", APP_FOO_ID),
          itfItem("bar", "1.0", APP_BAR_ID),
          itfItem("foo", "2.0", APP_BAR_ID),
          itfItem("qux", "1.0", APP_BAR_ID)
        ),
        Map.of(
          "foo", Set.of(itfItem("foo", "1.0", APP_FOO_ID)),
          "bar", Set.of(itfItem("bar", "1.0", APP_BAR_ID))
        )),
        List.of(
          new Parameter().key(APP_FOO_ID).value("baz 1.0"),
          new Parameter().key(APP_BAR_ID).value("foo 2.0; qux 1.0")
        )
      )
    );
  }

  private static EntitlementRequest entitlementRequest(String... applicationIds) {
    return EntitlementRequest.builder()
      .applications(List.of(applicationIds))
      .tenantId(TENANT_ID)
      .okapiToken(OKAPI_TOKEN)
      .type(ENTITLE)
      .build();
  }

  private static CommonStageContext stageContext(List<ApplicationDescriptor> applicationDescriptors,
    EntitlementRequest entitlementRequest) {
    var stageParameters = Map.of(PARAM_APP_DESCRIPTORS, applicationDescriptors);
    var flowParameters = Map.of(PARAM_REQUEST, entitlementRequest);
    return commonStageContext(FLOW_ID, flowParameters, stageParameters);
  }
}
