package org.folio.entitlement.support;

import static org.folio.common.utils.ExceptionHandlerUtils.buildValidationError;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.getField;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.FeignException.BadRequest;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.kafka.model.CapabilityEventBody;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.integration.kafka.model.SystemUserEvent;
import org.folio.flow.api.Stage;
import org.folio.flow.exception.StageExecutionException;
import org.folio.flow.model.StageResult;
import org.folio.security.domain.model.descriptor.RoutingEntry;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;

@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestUtils {

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
    .setSerializationInclusion(Include.NON_NULL)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

  private static final ClassLoader CLASS_LOADER = TestUtils.class.getClassLoader();

  @SneakyThrows
  public static String asJsonString(Object value) {
    return OBJECT_MAPPER.writeValueAsString(value);
  }

  @SneakyThrows
  public static <T> T parseResponse(MvcResult result, Class<T> type) {
    return OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), type);
  }

  @SneakyThrows
  public static <T> T parseResponse(MvcResult result, TypeReference<T> type) {
    return OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), type);
  }

  @SneakyThrows
  public static <T> T parse(String result, Class<T> type) {
    try {
      return OBJECT_MAPPER.readValue(result, type);
    } catch (JsonProcessingException e) {
      log.error("Failed to parse string: {}", result, e);
      return null;
    }
  }

  @SneakyThrows
  public static <T> T parse(String result, TypeReference<T> type) {
    try {
      return OBJECT_MAPPER.readValue(result, type);
    } catch (JsonProcessingException e) {
      log.error("Failed to parse string: {}", result, e);
      return null;
    }
  }

  @SneakyThrows
  public static <T> T parse(InputStream result, Class<T> type) {
    return OBJECT_MAPPER.readValue(result, type);
  }

  @SneakyThrows
  public static String readString(String path) {
    var resource = CLASS_LOADER.getResource(path);
    var file = new File(Objects.requireNonNull(resource).toURI());

    return FileUtils.readFileToString(file, StandardCharsets.UTF_8.name());
  }

  public static ApplicationDescriptor readApplicationDescriptor(String path) {
    return parse(readString(path), ApplicationDescriptor.class);
  }

  public static ResourceEvent<CapabilityEventBody> readCapabilityEvent(String path) {
    return parse(readString(path), new TypeReference<>() {});
  }

  public static ResourceEvent<RoutingEntry> readScheduledJobEvent(String path) {
    return parse(readString(path), new TypeReference<>() {});
  }

  public static ResourceEvent<SystemUserEvent> readSystemUserEvent(String path) {
    return parse(readString(path), new TypeReference<>() {});
  }

  @SneakyThrows
  public static List<StageResult> stageResults(Throwable error) {
    return ((StageExecutionException) error).getStageResults();
  }

  public static void mockStageNames(Stage... stages) {
    for (var stage : stages) {
      if (stage == null) {
        continue;
      }

      var stringValue = stage.toString();
      when(stage.getId()).thenReturn(stringValue);
    }
  }

  public static void verifyNoMoreInteractions(Object testClassInstance) {
    var declaredFields = testClassInstance.getClass().getDeclaredFields();
    var mocks = Arrays.stream(declaredFields)
      .filter(field -> field.getAnnotation(Mock.class) != null || field.getAnnotation(Spy.class) != null)
      .map(field -> getField(testClassInstance, field.getName()))
      .toArray();

    Mockito.verifyNoMoreInteractions(mocks);
  }

  public static BadRequest createBadRequest(RequestValidationException rve) {
    var errorResponse = buildValidationError(rve, rve.getErrorParameters());
    return createBadRequest(asJsonString(errorResponse));
  }

  public static BadRequest createBadRequest(String body) {
    var response = Response.builder()
      .request(getRequest("http://localhost"))
      .body(body, StandardCharsets.UTF_8)
      .headers(Collections.emptyMap())
      .status(HttpStatus.BAD_REQUEST.value())
      .reason(HttpStatus.BAD_REQUEST.getReasonPhrase())
      .build();

    return (BadRequest) FeignException.errorStatus("someMethod", response);
  }

  private static Request getRequest(String url) {
    return Request.create(Request.HttpMethod.GET, url, Collections.emptyMap(), null, (RequestTemplate) null);
  }
}
