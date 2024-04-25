package org.folio.entitlement.controller;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.logging.log4j.Level.DEBUG;
import static org.apache.logging.log4j.Level.WARN;
import static org.folio.common.domain.model.error.ErrorCode.AUTH_ERROR;
import static org.folio.common.domain.model.error.ErrorCode.FOUND_ERROR;
import static org.folio.common.domain.model.error.ErrorCode.NOT_FOUND_ERROR;
import static org.folio.common.domain.model.error.ErrorCode.SERVICE_ERROR;
import static org.folio.common.domain.model.error.ErrorCode.UNKNOWN_ERROR;
import static org.folio.common.domain.model.error.ErrorCode.VALIDATION_ERROR;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.utils.EntitlementServiceUtils.getErrorMessage;
import static org.folio.flow.model.ExecutionStatus.CANCELLATION_FAILED;
import static org.folio.flow.model.ExecutionStatus.CANCELLED;
import static org.folio.flow.model.ExecutionStatus.FAILED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;
import static org.springframework.http.ResponseEntity.badRequest;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.Level;
import org.folio.common.domain.model.error.Error;
import org.folio.common.domain.model.error.ErrorCode;
import org.folio.common.domain.model.error.ErrorResponse;
import org.folio.common.domain.model.error.Parameter;
import org.folio.cql2pgjson.exception.CQLFeatureUnsupportedException;
import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.service.FlowStageService;
import org.folio.flow.exception.FlowCancellationException;
import org.folio.flow.exception.FlowCancelledException;
import org.folio.flow.exception.FlowExecutionException;
import org.folio.flow.exception.StageExecutionException;
import org.folio.flow.model.ExecutionStatus;
import org.folio.flow.model.StageResult;
import org.folio.security.exception.ForbiddenException;
import org.folio.spring.cql.CqlQueryValidationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Log4j2
@RestControllerAdvice
@RequiredArgsConstructor
public class ApiExceptionHandler {

  public static final String FLOW_ID_HEADER = "x-mgr-tenant-entitlement-flow-id";
  public final FlowStageService flowStageService;

  /**
   * Catches and handles all exceptions for type {@link UnsupportedOperationException}.
   *
   * @param exception {@link UnsupportedOperationException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(UnsupportedOperationException.class)
  public ResponseEntity<ErrorResponse> handleUnsupportedOperationException(UnsupportedOperationException exception) {
    logException(DEBUG, exception);
    return buildResponseEntity(exception, NOT_IMPLEMENTED, SERVICE_ERROR);
  }

  /**
   * Catches and handles all exceptions for type {@link MethodArgumentNotValidException}.
   *
   * @param e {@link MethodArgumentNotValidException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
    logException(DEBUG, e);
    var validationErrors = Optional.of(e.getBindingResult()).map(Errors::getAllErrors).orElse(emptyList());
    var errorResponse = new ErrorResponse();
    validationErrors.forEach(error ->
      errorResponse.addErrorsItem(new Error()
        .message(error.getDefaultMessage())
        .code(VALIDATION_ERROR)
        .type(MethodArgumentNotValidException.class.getSimpleName())
        .addParametersItem(new Parameter()
          .key(((FieldError) error).getField())
          .value(String.valueOf(((FieldError) error).getRejectedValue())))));
    errorResponse.totalRecords(errorResponse.getErrors().size());

    return buildResponseEntity(errorResponse, BAD_REQUEST);
  }

  /**
   * Catches and handles all exceptions for type {@link ConstraintViolationException}.
   *
   * @param exception {@link ConstraintViolationException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
    logException(DEBUG, exception);
    var errorResponse = new ErrorResponse();
    exception.getConstraintViolations().forEach(constraintViolation ->
      errorResponse.addErrorsItem(new Error()
        .message(format("%s %s", constraintViolation.getPropertyPath(), constraintViolation.getMessage()))
        .code(VALIDATION_ERROR)
        .type(ConstraintViolationException.class.getSimpleName())));
    errorResponse.totalRecords(errorResponse.getErrors().size());

    return buildResponseEntity(errorResponse, BAD_REQUEST);
  }

  /**
   * Catches and handles all exceptions for type {@link RequestValidationException}.
   *
   * @param exception {@link RequestValidationException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(RequestValidationException.class)
  public ResponseEntity<ErrorResponse> handleRequestValidationException(RequestValidationException exception) {
    logException(DEBUG, exception);
    var errorResponse = buildValidationError(exception, exception.getErrorParameters());
    return buildResponseEntity(errorResponse, BAD_REQUEST);
  }

  /**
   * Catches and handles all exceptions for type {@link EntityExistsException}.
   *
   * @param exception {@link EntityExistsException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(EntityExistsException.class)
  public ResponseEntity<ErrorResponse> handleEntityExistsException(EntityExistsException exception) {
    logException(DEBUG, exception);
    return buildResponseEntity(exception, CONFLICT, FOUND_ERROR);
  }

  /**
   * Catches and handles common request validation exceptions.
   *
   * @param exception {@link Exception} object to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler({
    IllegalArgumentException.class,
    CqlQueryValidationException.class,
    MissingRequestHeaderException.class,
    CQLFeatureUnsupportedException.class,
    InvalidDataAccessApiUsageException.class,
    HttpMediaTypeNotSupportedException.class,
    MethodArgumentTypeMismatchException.class,
  })
  public ResponseEntity<ErrorResponse> handleValidationExceptions(Exception exception) {
    logException(DEBUG, exception);
    return buildResponseEntity(exception, BAD_REQUEST, VALIDATION_ERROR);
  }

  /**
   * Catches and handles all exceptions for type {@link EntityNotFoundException}.
   *
   * @param exception {@link EntityNotFoundException} object
   * @return {@link ResponseEntity} with {@link ErrorResponse} body.
   */
  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleEntityNotFoundException(EntityNotFoundException exception) {
    logException(DEBUG, exception);
    return buildResponseEntity(exception, NOT_FOUND, NOT_FOUND_ERROR);
  }

  /**
   * Catches and handles all exceptions for type {@link ForbiddenException}.
   *
   * @param exception {@link ForbiddenException} object
   * @return {@link ResponseEntity} with {@link ErrorResponse} body.
   */
  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleForbiddenException(ForbiddenException exception) {
    logException(DEBUG, exception);
    return buildResponseEntity(exception, FORBIDDEN, AUTH_ERROR);
  }

  /**
   * Catches and handles all exceptions for type {@link HttpMessageNotReadableException}.
   *
   * @param e {@link HttpMessageNotReadableException} object
   * @return {@link ResponseEntity} with {@link ErrorResponse} body.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handlerHttpMessageNotReadableException(HttpMessageNotReadableException e) {
    return Optional.ofNullable(e.getCause())
      .map(Throwable::getCause)
      .filter(IllegalArgumentException.class::isInstance)
      .map(IllegalArgumentException.class::cast)
      .map(this::handleValidationExceptions)
      .orElseGet(() -> {
        logException(DEBUG, e);
        return buildResponseEntity(e, BAD_REQUEST, VALIDATION_ERROR);
      });
  }

  /**
   * Catches and handles all exceptions for type {@link MissingServletRequestParameterException}.
   *
   * @param exception {@link MissingServletRequestParameterException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
    MissingServletRequestParameterException exception) {
    logException(DEBUG, exception);
    return buildResponseEntity(exception, BAD_REQUEST, VALIDATION_ERROR);
  }

  /**
   * Catches and handles all exceptions for type {@link IntegrationException}.
   *
   * @param exception {@link IntegrationException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(IntegrationException.class)
  public ResponseEntity<ErrorResponse> handleIntegrationException(IntegrationException exception) {
    logException(DEBUG, exception);

    var errorParameters = isEmpty(exception.getErrors())
      ? singletonList(new Parameter().key("cause").value(exception.getCause().getMessage()))
      : exception.getErrors();

    var errorResponse = buildErrorResponse(exception, errorParameters, SERVICE_ERROR);
    return buildResponseEntity(errorResponse, BAD_REQUEST);
  }

  /**
   * Catches and handles all exceptions for type {@link FlowExecutionException}.
   *
   * @param exception {@link FlowExecutionException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(StageExecutionException.class)
  public ResponseEntity<ErrorResponse> handleStageExecutionException(StageExecutionException exception) {
    var stageResults = exception.getStageResults();
    if (CollectionUtils.isEmpty(stageResults)) {
      logException(WARN, exception);
      return buildResponseEntity(exception, INTERNAL_SERVER_ERROR, SERVICE_ERROR);
    }

    logException(DEBUG, exception);
    var flowId = stageResults.get(0).getFlowId();

    var failedStages = flowStageService.findFailedStages(UUID.fromString(flowId));
    var executionStatus = getExecutionStatus(exception);

    var errorParameters = mapItems(failedStages, ApiExceptionHandler::getErrorParametersForStage);

    if (errorParameters.isEmpty()) {
      errorParameters = findLastFailedStage(stageResults)
        .map(ApiExceptionHandler::getErrorParametersForStage)
        .map(List::of)
        .orElseGet(Collections::emptyList);
    }

    var errorResponse = new ErrorResponse()
      .totalRecords(1)
      .addErrorsItem(new Error()
        .message(format("Flow '%s' finished with status: %s", flowId, executionStatus))
        .code(SERVICE_ERROR)
        .type(exception.getClass().getSimpleName())
        .parameters(errorParameters));

    return badRequest()
      .header(FLOW_ID_HEADER, flowId)
      .body(errorResponse);
  }

  /**
   * Handles all uncaught exceptions.
   *
   * @param exception {@link Exception} object
   * @return {@link ResponseEntity} with {@link ErrorResponse} body.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleAllOtherExceptions(Exception exception) {
    logException(WARN, exception);
    return buildResponseEntity(exception, INTERNAL_SERVER_ERROR, UNKNOWN_ERROR);
  }

  private static ErrorResponse buildValidationError(Exception exception, List<Parameter> parameters) {
    return buildErrorResponse(exception, parameters, VALIDATION_ERROR);
  }

  private static ErrorResponse buildErrorResponse(Exception exception, List<Parameter> parameters, ErrorCode code) {
    var error = new Error()
      .type(exception.getClass().getSimpleName())
      .code(code)
      .message(exception.getMessage())
      .parameters(parameters);
    return new ErrorResponse().errors(List.of(error)).totalRecords(1);
  }

  private static ResponseEntity<ErrorResponse> buildResponseEntity(Exception error, HttpStatus status, ErrorCode code) {
    var errorResponse = new ErrorResponse()
      .errors(List.of(new Error()
        .message(error.getMessage())
        .type(error.getClass().getSimpleName())
        .code(code)))
      .totalRecords(1);

    return buildResponseEntity(errorResponse, status);
  }

  private static ResponseEntity<ErrorResponse> buildResponseEntity(ErrorResponse errorResponse, HttpStatus status) {
    return ResponseEntity.status(status).body(errorResponse);
  }

  private static Parameter getErrorParametersForStage(FlowStage stage) {
    var stageName = stage.getName();
    var stageStatus = stage.getStatus();
    var errorMessage = format("%s: [%s] %s", stageStatus.name(), stage.getErrorType(), stage.getErrorMessage());
    return new Parameter().key(stageName).value(errorMessage);
  }

  private static Parameter getErrorParametersForStage(StageResult stageResult) {
    var stageName = stageResult.getStageId();
    var stageStatus = stageResult.getStatus();
    var error = stageResult.getError();
    var errorType = error.getClass().getSimpleName();
    var errorMessage = getErrorMessage(error);
    var fullMessage = format("%s: [%s] %s", stageStatus.name(), errorType, errorMessage);
    return new Parameter().key(stageName).value(fullMessage);
  }

  private static void logException(Level level, Exception exception) {
    log.log(level, "Handling exception", exception);
  }

  private static Optional<StageResult> findLastFailedStage(List<StageResult> stageResults) {
    var results = new ArrayList<>(stageResults);
    Collections.reverse(results);
    return results.stream()
      .filter(ApiExceptionHandler::isNotFinishedStage)
      .map(ApiExceptionHandler::getCauseStage)
      .flatMap(Optional::stream)
      .findFirst();
  }

  private static Optional<StageResult> getCauseStage(StageResult stageResult) {
    var subStageResults = stageResult.getSubStageResults();
    if (CollectionUtils.isEmpty(subStageResults)) {
      return Optional.of(stageResult);
    }

    return findLastFailedStage(subStageResults);
  }

  private static boolean isNotFinishedStage(StageResult stageResult) {
    var status = stageResult.getStatus();
    return status == FAILED || status == CANCELLED || status == CANCELLATION_FAILED;
  }

  private static ExecutionStatus getExecutionStatus(StageExecutionException exception) {
    if (exception instanceof FlowCancellationException) {
      return CANCELLATION_FAILED;
    }

    if (exception instanceof FlowCancelledException) {
      return CANCELLED;
    }

    return FAILED;
  }
}
