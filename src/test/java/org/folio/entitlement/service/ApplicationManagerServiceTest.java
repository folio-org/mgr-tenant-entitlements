package org.folio.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.folio.common.utils.ExceptionHandlerUtils.buildValidationError;
import static org.folio.entitlement.domain.model.ResultList.asSinglePage;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestUtils.createBadRequest;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.folio.entitlement.support.TestValues.moduleDiscovery;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException.BadRequest;
import feign.FeignException.InternalServerError;
import feign.FeignException.NotFound;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.folio.common.domain.model.error.ErrorResponse;
import org.folio.common.domain.model.error.Parameter;
import org.folio.common.utils.CqlQuery;
import org.folio.entitlement.domain.model.ResultList;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.am.ApplicationManagerClient;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationManagerServiceTest {

  @InjectMocks private ApplicationManagerService applicationManagerService;
  @Mock private ApplicationManagerClient applicationManagerClient;
  @Mock private ObjectMapper objectMapper;

  @Test
  void getApplicationDescriptor_positive() {
    var applicationDescriptor = applicationDescriptor();
    when(applicationManagerClient.getApplicationDescriptor(APPLICATION_ID, true, OKAPI_TOKEN))
      .thenReturn(applicationDescriptor);
    var actual = applicationManagerService.getApplicationDescriptor(APPLICATION_ID, OKAPI_TOKEN);
    assertThat(actual).isEqualTo(applicationDescriptor);
  }

  @Test
  void getApplicationDescriptor_negative_applicationDescriptorNotFound() {
    when(applicationManagerClient.getApplicationDescriptor(APPLICATION_ID, true, OKAPI_TOKEN))
      .thenThrow(NotFound.class);
    assertThatThrownBy(() -> applicationManagerService.getApplicationDescriptor(APPLICATION_ID, OKAPI_TOKEN))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage("Application descriptor is not found: " + APPLICATION_ID);
  }

  @Test
  void getApplicationDescriptor_negative_integrationError() {
    when(applicationManagerClient.getApplicationDescriptor(APPLICATION_ID, true, OKAPI_TOKEN))
      .thenThrow(InternalServerError.class);
    assertThatThrownBy(() -> applicationManagerService.getApplicationDescriptor(APPLICATION_ID, OKAPI_TOKEN))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("Failed to retrieve application descriptor: " + APPLICATION_ID)
      .hasCauseInstanceOf(InternalServerError.class);
  }

  @Test
  void getApplicationDescriptors_positive() {
    var applicationDescriptor = applicationDescriptor();
    var query = CqlQuery.exactMatchAny("id", List.of(APPLICATION_ID));
    ResultList<ApplicationDescriptor> resultList = asSinglePage(applicationDescriptor);
    when(applicationManagerClient.queryApplicationDescriptors(query, true, 1, 0, OKAPI_TOKEN))
      .thenReturn(resultList);
    var actual =
      applicationManagerService.getApplicationDescriptors(List.of(APPLICATION_ID), 1, 0, OKAPI_TOKEN);
    assertThat(actual).isEqualTo(resultList);
  }

  @Test
  void getApplicationDescriptors_negative_integrationError() {
    var query = CqlQuery.exactMatchAny("id", List.of(APPLICATION_ID));
    when(applicationManagerClient.queryApplicationDescriptors(query, true, 1, 0, OKAPI_TOKEN))
      .thenThrow(InternalServerError.class);
    assertThatThrownBy(
      () -> applicationManagerService.getApplicationDescriptors(List.of(APPLICATION_ID), 1, 0, OKAPI_TOKEN))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("Failed to query application descriptors")
      .hasCauseInstanceOf(InternalServerError.class);
  }

  @Test
  void getModuleDiscoveries_positive() {
    var expectedResultList = asSinglePage(moduleDiscovery());
    when(applicationManagerClient.getModuleDiscoveries(APPLICATION_ID, 100, OKAPI_TOKEN))
      .thenReturn(expectedResultList);
    var actual = applicationManagerService.getModuleDiscoveries(APPLICATION_ID, OKAPI_TOKEN);
    assertThat(actual).isEqualTo(expectedResultList);
  }

  @Test
  void getModuleDiscoveries_negative_integrationError() {
    when(applicationManagerClient.getModuleDiscoveries(APPLICATION_ID, 100, OKAPI_TOKEN))
      .thenThrow(InternalServerError.class);
    assertThatThrownBy(() -> applicationManagerService.getModuleDiscoveries(APPLICATION_ID, OKAPI_TOKEN))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("Failed to retrieve module discovery descriptors: " + APPLICATION_ID)
      .hasCauseInstanceOf(InternalServerError.class);
  }

  @Test
  void validate_positive() {
    var descriptor = applicationDescriptor();
    doNothing().when(applicationManagerClient).validate(descriptor, OKAPI_TOKEN);
    applicationManagerService.validate(descriptor, OKAPI_TOKEN);
    verify(applicationManagerClient).validate(descriptor, OKAPI_TOKEN);
  }

  @Test
  void validate_negative_validationException() {
    var descriptor = applicationDescriptor();
    doThrow(BadRequest.class).when(applicationManagerClient).validate(descriptor, OKAPI_TOKEN);
    assertThatThrownBy(() -> applicationManagerService.validate(descriptor, OKAPI_TOKEN))
      .isInstanceOf(RequestValidationException.class)
      .hasMessageContaining("Invalid application descriptor");
    verify(applicationManagerClient).validate(descriptor, OKAPI_TOKEN);
  }

  @Test
  void validate_negative_validationExceptionWithErrorResponse() throws JsonProcessingException {
    var descriptor = applicationDescriptor();

    var rve = new RequestValidationException("Application name is invalid", "name", "xxx");
    var badRequest = createBadRequest(rve);

    doThrow(badRequest).when(applicationManagerClient).validate(descriptor, OKAPI_TOKEN);
    when(objectMapper.readValue(anyString(), eq(ErrorResponse.class)))
      .thenReturn(buildValidationError(rve, rve.getErrorParameters()));

    assertThatThrownBy(() -> applicationManagerService.validate(descriptor, OKAPI_TOKEN))
      .isInstanceOf(RequestValidationException.class)
      .hasMessageContaining("Invalid application descriptor. Details: %s", rve.getMessage())
      .satisfies(throwable -> {
        var exc = (RequestValidationException) throwable;

        assertThat(exc.getErrorParameters()).containsExactly(
          new Parameter().key("application").value(descriptor.getId()));
      });
  }

  @Test
  void validate_negative_validationExceptionWithBadErrorResponse() throws JsonProcessingException {
    var descriptor = applicationDescriptor();

    var badRequest = createBadRequest("Not a valid ErrorResponse json");

    doThrow(badRequest).when(applicationManagerClient).validate(descriptor, OKAPI_TOKEN);
    doThrow(JsonProcessingException.class).when(objectMapper).readValue(anyString(), eq(ErrorResponse.class));

    assertThatThrownBy(() -> applicationManagerService.validate(descriptor, OKAPI_TOKEN))
      .isInstanceOf(RequestValidationException.class)
      .hasMessageContaining("Invalid application descriptor. Details: %s", badRequest.getMessage())
      .satisfies(throwable -> {
        var exc = (RequestValidationException) throwable;

        assertThat(exc.getErrorParameters()).containsExactly(
          new Parameter().key("application").value(descriptor.getId()));
      });
  }

  @Test
  void validate_negative_internalServerError() {
    var descriptor = applicationDescriptor();
    doThrow(InternalServerError.class).when(applicationManagerClient).validate(descriptor, OKAPI_TOKEN);
    assertThatThrownBy(() -> applicationManagerService.validate(descriptor, OKAPI_TOKEN))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("Failed to validate application descriptor: " + APPLICATION_ID);
    verify(applicationManagerClient).validate(descriptor, OKAPI_TOKEN);
  }
}
