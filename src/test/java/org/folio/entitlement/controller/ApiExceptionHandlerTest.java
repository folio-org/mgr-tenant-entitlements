package org.folio.entitlement.controller;

import static java.lang.String.format;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FAILED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestValues.singleThreadFlowEngine;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.EntitlementFlow;
import org.folio.entitlement.domain.dto.EntitlementStage;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.service.flow.EntitlementFlowService;
import org.folio.flow.api.Flow;
import org.folio.flow.api.FlowEngine;
import org.folio.flow.api.ParallelStage;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;
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
  @MockBean private EntitlementFlowService entitlementFlowService;

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
  void handleStageExecutionException_positive() throws Exception {
    var positiveFlow = positiveFlow();
    var failedFlow = failedFlow();
    var flowId = UUID.randomUUID();

    when(testService.execute()).then(inv -> executeFlow(flowId, List.of(positiveFlow, failedFlow)));

    var failedFlowId = UUID.fromString(failedFlow.getId());
    var positiveFlowId = UUID.fromString(positiveFlow.getId());
    when(entitlementFlowService.findById(flowId, true)).thenReturn(
      new EntitlementFlow().id(flowId).status(FAILED).applicationFlows(List.of(
        new ApplicationFlow().id(failedFlowId).status(FAILED).stages(List.of(
          new EntitlementStage().name("PositiveStage1").applicationFlowId(failedFlowId).status(FINISHED),
          new EntitlementStage().name("PositiveStage2").applicationFlowId(failedFlowId).status(FINISHED),
          new EntitlementStage().name("NegativeStage").applicationFlowId(failedFlowId).status(FAILED)
            .errorMessage("Invalid operation state").errorType("IllegalStateException"))),
        new ApplicationFlow().id(positiveFlowId).status(FINISHED).stages(List.of(
          new EntitlementStage().name("PositiveStage1").applicationFlowId(positiveFlowId).status(FINISHED),
          new EntitlementStage().name("PositiveStage2").applicationFlowId(positiveFlowId).status(FINISHED)))
      )));

    mockMvc.perform(get("/tests")
        .queryParam("query", "cql.allRecords=1")
        .contentType(APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is(format(
        "Application flow '%s' executed with status: FAILED", failedFlowId))))
      .andExpect(jsonPath("$.errors[0].type", is("FlowCancelledException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("PositiveStage1")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("FINISHED")))
      .andExpect(jsonPath("$.errors[0].parameters[1].key", startsWith("PositiveStage2")))
      .andExpect(jsonPath("$.errors[0].parameters[1].value", is("FINISHED")))
      .andExpect(jsonPath("$.errors[0].parameters[2].key", is("NegativeStage")))
      .andExpect(jsonPath("$.errors[0].parameters[2].value", is(
        "FAILED: [IllegalStateException] Invalid operation state")));
  }

  private Object executeFlow(UUID flowId, List<Flow> applicationFlows) {
    var flow = Flow.builder()
      .stage(ParallelStage.of(applicationFlows))
      .id(flowId.toString())
      .flowParameter(PARAM_APP_ID, APPLICATION_ID)
      .build();
    flowEngine.execute(flow);
    return null;
  }

  private static Flow positiveFlow() {
    return Flow.builder()
      .stage(new PositiveStage())
      .stage(ParallelStage.of(new ParallelStage1(), new ParallelStage2()))
      .id(UUID.randomUUID())
      .build();
  }

  private static Flow failedFlow() {
    return Flow.builder()
      .id(UUID.randomUUID())
      .stage(new PositiveStage())
      .stage(ParallelStage.of(new ParallelStage1(), new ParallelStage2()))
      .stage(new NegativeStage())
      .executionStrategy(IGNORE_ON_ERROR)
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
  private static final class TestRequest {

    @NotNull
    private UUID id;
  }

  private static final class PositiveStage implements Stage {

    @Override
    public void execute(StageContext context) {}

    @Override
    public String toString() {
      return "PositiveStage";
    }
  }

  private static final class NegativeStage implements Stage {

    @Override
    public void execute(StageContext context) {
      throw new IllegalStateException("Invalid operation state");
    }

    @Override
    public String toString() {
      return "NegativeStage";
    }
  }

  private static final class ParallelStage1 implements Stage {

    @Override
    public void execute(StageContext context) {}

    @Override
    public String toString() {
      return "ParallelStage1";
    }
  }

  private static final class ParallelStage2 implements Stage {

    @Override
    public void execute(StageContext context) {}

    @Override
    public String toString() {
      return "ParallelStage2";
    }
  }
}

