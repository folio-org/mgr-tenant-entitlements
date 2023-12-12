package org.folio.entitlement.service.validator;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.ExecutionStatus.CANCELLED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FAILED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.domain.dto.ExecutionStatus.QUEUED;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.flow.EntitlementFlowService;
import org.folio.entitlement.utils.SemverUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationFlowValidatorTest {

  @InjectMocks private ApplicationFlowValidator validator;
  @Mock private EntitlementFlowService entitlementFlowService;

  @MethodSource("positiveEntitlementEntitleFlowsDataProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  void validateEntitlementEntitleRequest_positive(@SuppressWarnings("unused") String name,
    EntitlementRequest request, List<ApplicationFlow> applicationFlows) {
    var applicationNames = request.getApplications().stream()
      .map(SemverUtils::getName)
      .distinct()
      .collect(Collectors.toList());
    var tenantId = request.getTenantId();
    when(entitlementFlowService.findLastFlowsByAppNames(applicationNames, tenantId)).thenReturn(applicationFlows);

    validator.validate(request);

    verify(entitlementFlowService).findLastFlowsByAppNames(applicationNames, tenantId);
  }

  @MethodSource("positiveEntitlementRevokeFlowsDataProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  void validateEntitlementRevokeRequest_positive(@SuppressWarnings("unused") String name,
    EntitlementRequest request, List<ApplicationFlow> applicationFlows) {
    var applicationId = request.getApplications();
    var tenantId = request.getTenantId();
    when(entitlementFlowService.findLastFlows(applicationId, tenantId)).thenReturn(applicationFlows);

    validator.validate(request);

    verify(entitlementFlowService).findLastFlows(applicationId, tenantId);
  }

  @MethodSource("negativeEntitlementEntitleFlowsDataProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  void validateEntitlementEntitleRequest_negative(@SuppressWarnings("unused") String name,
    EntitlementRequest request, List<ApplicationFlow> applicationFlows, List<Parameter> expectedParams) {
    var applicationNames = request.getApplications().stream()
      .map(SemverUtils::getName)
      .distinct()
      .collect(Collectors.toList());
    var tenantId = request.getTenantId();
    when(entitlementFlowService.findLastFlowsByAppNames(applicationNames, tenantId)).thenReturn(applicationFlows);

    assertThatThrownBy(() -> validator.validate(request))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Found validation errors in entitlement request")
      .satisfies(err -> assertThat(((RequestValidationException) err).getErrorParameters()).isEqualTo(expectedParams));

    verify(entitlementFlowService).findLastFlowsByAppNames(applicationNames, tenantId);
  }

  @MethodSource("negativeEntitlementRevokeFlowsDataProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  void validateEntitlementRevokeRequest_negative(@SuppressWarnings("unused") String name,
    EntitlementRequest request, List<ApplicationFlow> applicationFlows, List<Parameter> expectedParams) {
    var applicationId = request.getApplications();
    var tenantId = request.getTenantId();
    when(entitlementFlowService.findLastFlows(applicationId, tenantId)).thenReturn(applicationFlows);

    assertThatThrownBy(() -> validator.validate(request))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Found validation errors in entitlement request")
      .satisfies(err -> assertThat(((RequestValidationException) err).getErrorParameters()).isEqualTo(expectedParams));

    verify(entitlementFlowService).findLastFlows(applicationId, tenantId);
  }

  @ParameterizedTest
  @DisplayName("shouldValidate_parameterized")
  @CsvSource({"ENTITLE, true", "REVOKE, true"})
  void shouldValidate_parameterized(EntitlementType type, boolean expected) {
    var request = EntitlementRequest.builder().type(type).build();
    var result = validator.shouldValidate(request);
    assertThat(result).isEqualTo(expected);
  }

  private static Stream<Arguments> positiveEntitlementRevokeFlowsDataProvider() {
    return Stream.of(
      arguments("Revoke: no application flows found", request(REVOKE), emptyList()),
      arguments("Revoke: entitled flow found", request(REVOKE), List.of(flow(ENTITLE, FINISHED))),
      arguments("Revoke: failed entitle flow found", request(REVOKE), List.of(flow(REVOKE, FAILED))),
      arguments("Revoke: cancelled entitle flow found", request(REVOKE), List.of(flow(REVOKE, CANCELLED)))
    );
  }

  private static Stream<Arguments> positiveEntitlementEntitleFlowsDataProvider() {
    return Stream.of(
      arguments("Entitle: no application flows found", request(ENTITLE), emptyList()),
      arguments("Entitle: revoked flow found", request(ENTITLE), List.of(flow(REVOKE, FINISHED))),
      arguments("Entitle: failed entitle flow found", request(ENTITLE), List.of(flow(ENTITLE, FAILED))),
      arguments("Entitle: cancelled entitle flow found", request(ENTITLE), List.of(flow(ENTITLE, CANCELLED))),

      arguments("Entitle: multiple application flows", request(ENTITLE, "app1", "app2"), emptyList()),
      arguments("Entitle: multiple revoked application flows", request(ENTITLE, "app1", "app2"),
        List.of(flow("app1", REVOKE, FINISHED), flow("app2", REVOKE, FINISHED)))
    );
  }

  private static Stream<Arguments> negativeEntitlementRevokeFlowsDataProvider() {
    return Stream.of(
      arguments("Revoke: entitled flow found",
        request(REVOKE), List.of(flow(REVOKE, FINISHED)), List.of(parameter("Revoke flow finished"))),
      arguments("Revoke: queued flow found",
        request(REVOKE), List.of(flow(REVOKE, QUEUED)), List.of(parameter("Revoke flow is in queue"))),
      arguments("Revoke: in progress flow found",
        request(REVOKE), List.of(flow(REVOKE, IN_PROGRESS)), List.of(parameter("Revoke flow is in progress"))),
      arguments("Revoke: queued revoke flow found",
        request(REVOKE), List.of(flow(ENTITLE, QUEUED)), List.of(parameter("Another entitle flow is in queue"))),
      arguments("Revoke: in progress revoke flow found",
        request(REVOKE), List.of(flow(ENTITLE, IN_PROGRESS)),
        List.of(parameter("Another entitle flow is in progress"))),
      arguments("Revoke: failed revoke flow found",
        request(REVOKE), List.of(flow(ENTITLE, FAILED)), List.of(parameter("Previous entitle flow failed"))),
      arguments("Revoke: cancelled flow found",
        request(REVOKE), List.of(flow(ENTITLE, CANCELLED)), List.of(parameter("Previous entitle flow canceled")))
    );
  }

  private static Stream<Arguments> negativeEntitlementEntitleFlowsDataProvider() {
    return Stream.of(
      arguments("Entitle: entitled flow found",
        request(ENTITLE), List.of(flow(ENTITLE, FINISHED)), List.of(parameter("Entitle flow finished"))),
      arguments("Entitle: queued flow found",
        request(ENTITLE), List.of(flow(ENTITLE, QUEUED)), List.of(parameter("Entitle flow is in queue"))),
      arguments("Entitle: in progress flow found",
        request(ENTITLE), List.of(flow(ENTITLE, IN_PROGRESS)), List.of(parameter("Entitle flow is in progress"))),
      arguments("Entitle: queued revoke flow found",
        request(ENTITLE), List.of(flow(REVOKE, QUEUED)), List.of(parameter("Another revoke flow is in queue"))),
      arguments("Entitle: in progress revoke flow found",
        request(ENTITLE), List.of(flow(REVOKE, IN_PROGRESS)), List.of(parameter("Another revoke flow is in progress"))),
      arguments("Entitle: failed revoke flow found",
        request(ENTITLE), List.of(flow(REVOKE, FAILED)), List.of(parameter("Previous revoke flow failed"))),
      arguments("Entitle: cancelled revoke flow found",
        request(ENTITLE), List.of(flow(REVOKE, CANCELLED)), List.of(parameter("Previous revoke flow canceled")))
    );
  }

  private static EntitlementRequest request(EntitlementType type) {
    return request(type, APPLICATION_ID);
  }

  private static EntitlementRequest request(EntitlementType type, String... applicationIds) {
    return EntitlementRequest.builder()
      .tenantId(TENANT_ID)
      .applications(Arrays.asList(applicationIds))
      .type(type)
      .build();
  }

  private static Parameter parameter(String errorMessage) {
    return new Parameter().key(APPLICATION_ID).value(errorMessage);
  }

  private static ApplicationFlow flow(EntitlementType type, ExecutionStatus status) {
    return flow(APPLICATION_ID, type, status);
  }

  private static ApplicationFlow flow(String applicationId, EntitlementType type, ExecutionStatus status) {
    return new ApplicationFlow().type(type).status(status).applicationId(applicationId).tenantId(TENANT_ID);
  }
}
