package org.folio.entitlement.controller;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.folio.entitlement.controller.ApiExceptionHandler.FLOW_ID_HEADER;
import static org.folio.entitlement.domain.dto.ExecutionStatus.CANCELLATION_FAILED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.CANCELLED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FAILED;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestValues.singleThreadFlowEngine;
import static org.folio.flow.model.FlowExecutionStrategy.CANCEL_ON_ERROR;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.service.FlowStageService;
import org.folio.flow.api.DynamicStage;
import org.folio.flow.api.Flow;
import org.folio.flow.api.FlowEngine;
import org.folio.flow.api.ParallelStage;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;
import org.folio.flow.model.FlowExecutionStrategy;
import org.folio.security.exception.ForbiddenException;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@UnitTest
@WebMvcTest(ApiExceptionHandlerTest.TestController.class)
@Import({ControllerTestConfiguration.class, ApiExceptionHandlerTest.TestController.class})
class ApiExceptionHandlerTest {

  private final FlowEngine flowEngine = singleThreadFlowEngine("api-exception-handler-fe", false);

  @Autowired private MockMvc mockMvc;
  @MockBean private TestService testService;
  @MockBean private FlowStageService flowStageService;

  @Test
  void handleUnsupportedOperationException_positive() throws Exception {
    when(testService.execute()).thenThrow(new UnsupportedOperationException("Operation is not supported"));
    mockMvc.perform(get("/tests").queryParam("query", "cql.allRecords=1").contentType(APPLICATION_JSON))
      .andExpect(status().isNotImplemented())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Operation is not supported")))
      .andExpect(jsonPath("$.errors[0].type", is("UnsupportedOperationException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")));
  }

  @Test
  void handleRequestValidationException_positive_keyAndValueArePresent() throws Exception {
    when(testService.execute()).thenThrow(new RequestValidationException("validation error", "key", "value"));
    mockMvc.perform(get("/tests").queryParam("query", "cql.allRecords=1").contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("validation error")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("key")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("value")));
  }

  @Test
  void handleEntityNotFoundException_positive() throws Exception {
    when(testService.execute()).thenThrow(new EntityNotFoundException("Entity not found"));
    mockMvc.perform(get("/tests").queryParam("query", "cql.allRecords=1").contentType(APPLICATION_JSON))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Entity not found")))
      .andExpect(jsonPath("$.errors[0].type", is("EntityNotFoundException")))
      .andExpect(jsonPath("$.errors[0].code", is("not_found_error")));
  }

  @Test
  void handleEntityExistsException_positive() throws Exception {
    when(testService.execute()).thenThrow(new EntityExistsException("error"));
    mockMvc.perform(get("/tests").queryParam("query", "cql.allRecords=1").contentType(APPLICATION_JSON))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("error")))
      .andExpect(jsonPath("$.errors[0].type", is("EntityExistsException")))
      .andExpect(jsonPath("$.errors[0].code", is("found_error")));
  }

  @Test
  void handleAllOtherExceptions_positive() throws Exception {
    when(testService.execute()).thenThrow(new NullPointerException());
    mockMvc.perform(get("/tests")
        .queryParam("query", "cql.allRecords=1")
        .contentType(APPLICATION_JSON))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].type", is("NullPointerException")))
      .andExpect(jsonPath("$.errors[0].code", is("unknown_error")));
  }

  @Test
  void handleRuntimeError_positive() throws Exception {
    when(testService.execute()).thenThrow(new RuntimeException("error"));
    mockMvc.perform(get("/tests")
        .queryParam("query", "cql.allRecords=1")
        .contentType(APPLICATION_JSON))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("error")))
      .andExpect(jsonPath("$.errors[0].type", is("RuntimeException")))
      .andExpect(jsonPath("$.errors[0].code", is("unknown_error")));
  }

  @Test
  void handleIntegrationException_positive() throws Exception {
    var cause = new RuntimeException("404 Not Found");
    when(testService.execute()).thenThrow(new IntegrationException("Failed to perform request", cause));
    mockMvc.perform(get("/tests").queryParam("query", "cql.allRecords=1").contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Failed to perform request")))
      .andExpect(jsonPath("$.errors[0].type", is("IntegrationException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("cause")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("404 Not Found")));
  }

  @Test
  void handleIntegrationException_positive_errorParameters() throws Exception {
    var errorParams = List.of(new Parameter().key("test-route-id").value("409 Conflict"));
    when(testService.execute()).thenThrow(new IntegrationException("Failed to create routes", errorParams));
    mockMvc.perform(get("/tests").queryParam("query", "cql.allRecords=1").contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Failed to create routes")))
      .andExpect(jsonPath("$.errors[0].type", is("IntegrationException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("test-route-id")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("409 Conflict")));
  }

  @Test
  void handleConstraintViolationException_positive() throws Exception {
    mockMvc.perform(get("/tests")
        .queryParam("limit", "10000")
        .queryParam("query", "cql.allRecords=1")
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("getTests.limit must be less than or equal to 500")))
      .andExpect(jsonPath("$.errors[0].type", is("ConstraintViolationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void handleMissingServletRequestParameterException_positive() throws Exception {
    var errorMessage = "Required request parameter 'query' for method parameter type String is not present";
    mockMvc.perform(get("/tests").queryParam("limit", "10000").contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(errorMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("MissingServletRequestParameterException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void handleValidationExceptions_positive_httpMediaTypeNotSupportedException() throws Exception {
    mockMvc.perform(get("/tests").contentType(TEXT_PLAIN))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Content-Type 'text/plain' is not supported")))
      .andExpect(jsonPath("$.errors[0].type", is("HttpMediaTypeNotSupportedException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void handleMethodArgumentNotValidException_positive() throws Exception {
    mockMvc.perform(put("/tests/{id}", UUID.randomUUID())
        .content("{\"key\": \"value\"}")
        .contentType(APPLICATION_JSON)
        .header(CACHE_CONTROL, "no-cache"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("must not be null")))
      .andExpect(jsonPath("$.errors[0].type", is("MethodArgumentNotValidException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("id")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("null")));
  }

  @Test
  void handleForbiddenException_positive() throws Exception {
    when(testService.execute()).thenThrow(new ForbiddenException("error"));
    mockMvc.perform(get("/tests").queryParam("query", "cql.allRecords=1").contentType(APPLICATION_JSON))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("error")))
      .andExpect(jsonPath("$.errors[0].type", is("ForbiddenException")))
      .andExpect(jsonPath("$.errors[0].code", is("auth_error")));
  }

  @Test
  void handleValidationExceptions_positive_headerIsMissing() throws Exception {
    var errorMessage = "Required request header 'Cache-Control' for method parameter type String is not present";
    mockMvc.perform(put("/tests/{id}", UUID.randomUUID())
        .content("{\"id\": \"8edfff61-d2c8-401b-afed-6348a9d855b2\"}")
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(errorMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("MissingRequestHeaderException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void handleValidationExceptions_positive_invalidIdInPath() throws Exception {
    var errorMessage = "Failed to convert value of type 'java.lang.String' to required type 'java.util.UUID'";
    mockMvc.perform(put("/tests/{id}", "resource-id")
        .content("{\"id\": \"8edfff61-d2c8-401b-afed-6348a9d855b2\"}")
        .contentType(APPLICATION_JSON)
        .header(CACHE_CONTROL, "no-cache"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", startsWith(errorMessage)))
      .andExpect(jsonPath("$.errors[0].type", is("MethodArgumentTypeMismatchException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void handleHttpMessageNotReadableException_positive() throws Exception {
    mockMvc.perform(put("/tests/{id}", UUID.randomUUID())
        .content("\"key\": \"8edfff61-d2c8-401b-afed-6348a9d855b2\"}")
        .contentType(APPLICATION_JSON)
        .header(CACHE_CONTROL, "no-cache"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", startsWith("JSON parse error: Cannot construct instance of")))
      .andExpect(jsonPath("$.errors[0].type", is("HttpMessageNotReadableException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void handleStageExecutionException_positive_failedFlow() throws Exception {
    var failedFlow = failedInstallationFlow(IGNORE_ON_ERROR, new NegativeStage1());
    var failedStage = new FlowStage()
      .name("NegativeStage1")
      .flowId(APPLICATION_FLOW_ID)
      .status(FAILED)
      .errorType("RuntimeException")
      .errorMessage("Stage error");

    when(testService.execute()).then(inv -> executeFlow(failedFlow));
    when(flowStageService.findFailedStages(FLOW_ID)).thenReturn(List.of(failedStage));

    mockMvc.perform(get("/tests")
        .queryParam("query", "cql.allRecords=1")
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(header().string(FLOW_ID_HEADER, is(FLOW_ID.toString())))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(format("Flow '%s' finished with status: FAILED", FLOW_ID))))
      .andExpect(jsonPath("$.errors[0].type", is("FlowExecutionException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("NegativeStage1")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("FAILED: [RuntimeException] Stage error")));
  }

  @Test
  void handleStageExecutionException_positive_failedFlowWithEmptyDatabaseResult() throws Exception {
    var failedFlow = failedInstallationFlow(IGNORE_ON_ERROR, new NegativeStage1());

    when(testService.execute()).then(inv -> executeFlow(failedFlow));
    when(flowStageService.findFailedStages(FLOW_ID)).thenReturn(emptyList());

    mockMvc.perform(get("/tests")
        .queryParam("query", "cql.allRecords=1")
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(header().string(FLOW_ID_HEADER, is(FLOW_ID.toString())))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(format("Flow '%s' finished with status: FAILED", FLOW_ID))))
      .andExpect(jsonPath("$.errors[0].type", is("FlowExecutionException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("NegativeStage1")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("FAILED: [RuntimeException] Stage error")));
  }

  @Test
  void handleStageExecutionException_positive_cancelledFlow() throws Exception {
    var failedFlow = failedInstallationFlow(CANCEL_ON_ERROR, new NegativeStage1());
    var failedStage = new FlowStage()
      .name("NegativeStage1")
      .flowId(APPLICATION_FLOW_ID)
      .status(FAILED)
      .errorType("RuntimeException")
      .errorMessage("Stage error");

    when(testService.execute()).then(inv -> executeFlow(failedFlow));
    when(flowStageService.findFailedStages(FLOW_ID)).thenReturn(List.of(failedStage));

    mockMvc.perform(get("/tests")
        .queryParam("query", "cql.allRecords=1")
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(header().string(FLOW_ID_HEADER, is(FLOW_ID.toString())))
      .andExpect(header().string(FLOW_ID_HEADER, is(FLOW_ID.toString())))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(format("Flow '%s' finished with status: CANCELLED", FLOW_ID))))
      .andExpect(jsonPath("$.errors[0].type", is("FlowCancelledException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("NegativeStage1")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("FAILED: [RuntimeException] Stage error")));
  }

  @Test
  void handleStageExecutionException_positive_cancelledFlowAndCancelledStage() throws Exception {
    var failedFlow = failedInstallationFlow(CANCEL_ON_ERROR, new NegativeStage2());
    var failedStage = new FlowStage()
      .name("NegativeStage2")
      .flowId(APPLICATION_FLOW_ID)
      .status(CANCELLED)
      .errorType("RuntimeException")
      .errorMessage("Stage error");

    when(testService.execute()).then(inv -> executeFlow(failedFlow));
    when(flowStageService.findFailedStages(FLOW_ID)).thenReturn(List.of(failedStage));

    mockMvc.perform(get("/tests")
        .queryParam("query", "cql.allRecords=1")
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(header().string(FLOW_ID_HEADER, is(FLOW_ID.toString())))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(format("Flow '%s' finished with status: CANCELLED", FLOW_ID))))
      .andExpect(jsonPath("$.errors[0].type", is("FlowCancelledException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("NegativeStage2")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("CANCELLED: [RuntimeException] Stage error")));
  }

  @Test
  void handleStageExecutionException_positive_cancelledFlowAndFailedToCancelStage() throws Exception {
    var negativeStageSpy = spy(new NegativeStage2());
    doThrow(new RuntimeException((String) null)).when(negativeStageSpy).cancel(any());
    var failedFlow = failedInstallationFlow(CANCEL_ON_ERROR, negativeStageSpy);
    var flowId = UUID.fromString(failedFlow.getId());
    var failedStage = new FlowStage()
      .name("NegativeStage2")
      .flowId(UUID.randomUUID())
      .status(CANCELLATION_FAILED)
      .errorType("RuntimeException")
      .errorMessage("null");

    when(testService.execute()).then(inv -> executeFlow(failedFlow));
    when(flowStageService.findFailedStages(flowId)).thenReturn(List.of(failedStage));

    mockMvc.perform(get("/tests")
        .queryParam("query", "cql.allRecords=1")
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(header().string(FLOW_ID_HEADER, is(flowId.toString())))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(format(
        "Flow '%s' finished with status: CANCELLATION_FAILED", flowId))))
      .andExpect(jsonPath("$.errors[0].type", is("FlowCancellationException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("NegativeStage2")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("CANCELLATION_FAILED: [RuntimeException] null")));
  }

  private Object executeFlow(Flow flow) {
    flowEngine.execute(flow);
    return null;
  }

  private static Flow failedInstallationFlow(FlowExecutionStrategy strategy, Stage<StageContext> negativeStage) {
    var subflow1 = positiveApplicationFlow(strategy);
    var subflow2 = negativeApplicationFlow(strategy, negativeStage);

    return Flow.builder()
      .id(FLOW_ID)
      .stage(new PositiveStage())
      .stage(DynamicStage.of("ApplicationsFlowProvider", ctx -> Flow.builder()
        .id(ctx.flowId() + "/ApplicationsFlow")
        .stage(ParallelStage.of("Level-0", asList(subflow1, subflow2)))
        .onFlowError(new PositiveStage())
        .onFlowCancellation(new PositiveStage())
        .build()))
      .onFlowError(new PositiveStage())
      .onFlowCancellation(new PositiveStage())
      .executionStrategy(strategy)
      .build();
  }

  private static Flow positiveApplicationFlow(FlowExecutionStrategy strategy) {
    return Flow.builder()
      .id(FLOW_ID + "/ApplicationsFlow/Level-0/" + UUID.randomUUID())
      .stage(new PositiveStage())
      .stage(DynamicStage.of("FolioModuleInstallerProvider", ctx -> Flow.builder()
        .id(ctx.flowId() + "/FolioModuleInstaller")
        .stage(new PositiveStage())
        .stage(new PositiveStage())
        .build()))
      .stage(new PositiveStage())
      .onFlowError(new PositiveStage())
      .onFlowCancellation(new PositiveStage())
      .executionStrategy(strategy)
      .build();
  }

  private static Flow negativeApplicationFlow(FlowExecutionStrategy strategy, Stage<StageContext> negativeStage) {
    return Flow.builder()
      .id(FLOW_STAGE_ID)
      .stage(new PositiveStage())
      .stage(DynamicStage.of("FolioModuleInstallerProvider ", ctx -> Flow.builder()
        .id(ctx.flowId() + "/FolioModuleInstaller ")
        .stage(new PositiveStage())
        .stage(negativeStage)
        .build()))
      .stage(new PositiveStage())
      .onFlowError(new PositiveStage())
      .onFlowCancellation(new PositiveStage())
      .executionStrategy(strategy)
      .build();
  }

  @Validated
  @RestController
  @RequiredArgsConstructor
  static class TestController {

    private final TestService testService;

    @SuppressWarnings("unused")
    @GetMapping(value = "/tests", consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getTests(
      @RequestParam(value = "query") String query,
      @Min(0) @Valid @RequestParam(value = "offset", required = false, defaultValue = "0") Integer offset,
      @Min(0) @Max(500) @Valid @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit) {
      return ResponseEntity.ok(testService.execute());
    }

    @SuppressWarnings("unused")
    @PutMapping(value = "/tests/{id}", consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> putTest(
      @PathVariable(value = "id") UUID id,
      @Valid @RequestBody TestRequest request,
      @RequestHeader(CACHE_CONTROL) String cacheControlHeader) {
      return ResponseEntity.ok(testService.execute());
    }
  }

  static class TestService {

    public Object execute() {
      return "test value";
    }
  }

  @Data
  static final class TestRequest {

    @NotNull
    private UUID id;
  }

  private static final class PositiveStage implements Stage<StageContext> {

    @Override
    public void execute(StageContext context) {}

    @Override
    public void cancel(StageContext context) {}

    @Override
    public String toString() {
      return "PositiveStage";
    }
  }

  private static final class NegativeStage1 implements Stage<StageContext> {

    @Override
    public void execute(StageContext context) {
      throw new RuntimeException("Stage error");
    }

    @Override
    public String toString() {
      return "NegativeStage1";
    }
  }

  private static final class NegativeStage2 implements Stage<StageContext> {

    @Override
    public void execute(StageContext context) {
      throw new RuntimeException("Stage error");
    }

    @Override
    public void cancel(StageContext context) {}

    @Override
    public boolean shouldCancelIfFailed(StageContext context) {
      return true;
    }

    @Override
    public String toString() {
      return "NegativeStage2";
    }
  }
}

