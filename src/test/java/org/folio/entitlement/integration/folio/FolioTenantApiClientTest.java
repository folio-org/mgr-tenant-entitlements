package org.folio.entitlement.integration.folio;

import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.common.utils.OkapiHeaders.TENANT;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestUtils.JACKSON3_OBJECT_MAPPER;
import static org.folio.entitlement.support.TestUtils.asJsonString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.LOCATION;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.lang3.SerializationException;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.folio.configuration.FolioClientConfigurationProperties;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.integration.folio.model.TenantAttributes;
import org.folio.entitlement.integration.folio.model.TenantJob;
import org.folio.entitlement.integration.folio.model.TenantOperation;
import org.folio.entitlement.support.TestUtils;
import org.folio.entitlement.utils.JsonConverter;
import org.folio.test.types.UnitTest;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FolioTenantApiClientTest {

  private static final String MODULE_ID = "mod-test-2.1";
  private static final String MODULE_LOCATION = "http://mod-test:8081";
  private static final String JOB_ID = "6a3b3c1e-1111-4222-8333-444455556666";

  @InjectMocks private FolioTenantApiClient folioTenantApiClient;
  @Mock private HttpClient httpClient;
  @Mock private HttpResponse<String> httpResponse;
  @Mock private FolioClientConfigurationProperties folioClientConfigurationProperties;
  @Captor private ArgumentCaptor<HttpRequest> httpRequestCaptor;
  @Spy private final JsonConverter jsonConverter = new JsonConverter(JACKSON3_OBJECT_MAPPER);

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void install_positive() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenReturn(httpResponse);
    when(httpResponse.statusCode()).thenReturn(204);

    var parameters = List.of(new Parameter().key("loadReference").value("true"));
    var operation = folioTenantApiClient.install(moduleRequest(parameters));

    assertThat(operation).isEqualTo(TenantOperation.completed());
    verifyHttpRequest(installRequest(), httpRequestCaptor.getValue(), 82);
    verify(jsonConverter).toJson(tenantAttributesForInstall());
  }

  @Test
  void install_positive_statusEqualToCreatedWithoutJob() throws IOException, InterruptedException {
    setupHttpClientMock(201);
    when(httpResponse.headers()).thenReturn(headers(Map.of()));
    when(httpResponse.body()).thenReturn("");

    var parameters = List.of(new Parameter().key("loadReference").value("true"));
    var operation = folioTenantApiClient.install(moduleRequest(parameters));

    assertThat(operation).isEqualTo(TenantOperation.completed());
    verifyHttpRequest(installRequest(), httpRequestCaptor.getValue(), 82);
    verify(jsonConverter).toJson(tenantAttributesForInstall());
  }

  @Test
  void install_positive_statusOkIsCompleted() throws IOException, InterruptedException {
    setupHttpClientMock(200);
    var parameters = List.of(new Parameter().key("loadReference").value("true"));

    var operation = folioTenantApiClient.install(moduleRequest(parameters));

    assertThat(operation).isEqualTo(TenantOperation.completed());
    verifyHttpRequest(installRequest(), httpRequestCaptor.getValue(), 82);
    verify(jsonConverter).toJson(tenantAttributesForInstall());
  }

  @ParameterizedTest
  @MethodSource("jobLocationsProvider")
  void install_positive_createdWithJobLocation(String location) throws IOException, InterruptedException {
    setupHttpClientMock(201);
    when(httpResponse.headers()).thenReturn(headers(Map.of(LOCATION, List.of(location))));
    var parameters = List.of(new Parameter().key("loadReference").value("true"));

    var operation = folioTenantApiClient.install(moduleRequest(parameters));

    assertThat(operation).isEqualTo(TenantOperation.accepted(JOB_ID));
    verifyHttpRequest(installRequest(), httpRequestCaptor.getValue(), 82);
    verify(jsonConverter).toJson(tenantAttributesForInstall());
  }

  @ParameterizedTest
  @MethodSource("invalidLocationsProvider")
  void install_positive_createdWithInvalidLocationIsTreatedAsCompleted(String location)
    throws IOException, InterruptedException {
    setupHttpClientMock(201);
    when(httpResponse.headers()).thenReturn(headers(Map.of(LOCATION, List.of(location))));
    var parameters = List.of(new Parameter().key("loadReference").value("true"));

    var operation = folioTenantApiClient.install(moduleRequest(parameters));

    assertThat(operation).isEqualTo(TenantOperation.completed());
    verify(jsonConverter).toJson(tenantAttributesForInstall());
  }

  @Test
  void install_positive_createdWithJobInBodyOnly() throws IOException, InterruptedException {
    var body = "{\"id\":\"" + JOB_ID + "\",\"complete\":false}";
    setupHttpClientMock(201);
    when(httpResponse.headers()).thenReturn(headers(Map.of()));
    when(httpResponse.body()).thenReturn(body);
    var parameters = List.of(new Parameter().key("loadReference").value("true"));

    var operation = folioTenantApiClient.install(moduleRequest(parameters));

    assertThat(operation).isEqualTo(TenantOperation.accepted(JOB_ID));
    verify(jsonConverter).toJson(tenantAttributesForInstall());
    verify(jsonConverter).fromJson(body, TenantJob.class);
  }

  @ParameterizedTest
  @MethodSource("unusableBodiesProvider")
  void install_positive_createdWithUnusableBodyIsTreatedAsCompleted(String body)
    throws IOException, InterruptedException {
    setupHttpClientMock(201);
    when(httpResponse.headers()).thenReturn(headers(Map.of()));
    when(httpResponse.body()).thenReturn(body);
    var parameters = List.of(new Parameter().key("loadReference").value("true"));

    var operation = folioTenantApiClient.install(moduleRequest(parameters));

    assertThat(operation).isEqualTo(TenantOperation.completed());
    verify(jsonConverter).toJson(tenantAttributesForInstall());
    verify(jsonConverter).fromJson(body, TenantJob.class);
  }

  @Test
  void install_negative_invalidResponse() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenReturn(httpResponse);
    when(httpResponse.statusCode()).thenReturn(500);
    when(httpResponse.body()).thenReturn("Failed to install tenant");

    var parameters = List.of(new Parameter().key("loadReference").value("true"));
    var moduleRequest = moduleRequest(parameters);
    assertThatThrownBy(() -> folioTenantApiClient.install(moduleRequest))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("Failed to perform doPostTenant call")
      .extracting(e -> ((IntegrationException) e).getCauseHttpStatus()).isEqualTo(500);

    verifyHttpRequest(installRequest(), httpRequestCaptor.getValue(), 82);
    verify(jsonConverter).toJson(tenantAttributesForInstall());
  }

  @Test
  void install_negative_ioException() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenThrow(IOException.class);

    var parameters = List.of(new Parameter().key("loadReference").value("true"));
    var moduleRequest = moduleRequest(parameters);
    assertThatThrownBy(() -> folioTenantApiClient.install(moduleRequest))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("[IOException] Failed to perform request [method: POST, uri: http://mod-test:8081/_/tenant]");

    verifyHttpRequest(installRequest(), httpRequestCaptor.getValue(), 82);
    verify(jsonConverter).toJson(tenantAttributesForInstall());
  }

  @Test
  void install_negative_interruptedException() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenThrow(InterruptedException.class);

    var parameters = List.of(new Parameter().key("loadReference").value("true"));
    var moduleRequest = moduleRequest(parameters);
    assertThatThrownBy(() -> folioTenantApiClient.install(moduleRequest))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("Request has been interrupted [method: POST, uri: http://mod-test:8081/_/tenant]");

    verifyHttpRequest(installRequest(), httpRequestCaptor.getValue(), 82);
    verify(jsonConverter).toJson(tenantAttributesForInstall());
  }

  @Test
  void uninstall_positive() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenReturn(httpResponse);
    when(httpResponse.statusCode()).thenReturn(204);

    var moduleRequest = moduleRequest(true);
    var operation = folioTenantApiClient.uninstall(moduleRequest);

    assertThat(operation).isEqualTo(TenantOperation.completed());
    verifyHttpRequest(uninstallRequest(), httpRequestCaptor.getValue(), 43);
    verify(jsonConverter).toJson(tenantAttributesForUninstall());
  }

  @Test
  void uninstall_positive_createdWithJobLocation() throws IOException, InterruptedException {
    setupHttpClientMock(201);
    when(httpResponse.headers()).thenReturn(headers(Map.of(LOCATION, List.of("/_/tenant/" + JOB_ID))));

    var operation = folioTenantApiClient.uninstall(moduleRequest(true));

    assertThat(operation).isEqualTo(TenantOperation.accepted(JOB_ID));
    verifyHttpRequest(uninstallRequest(), httpRequestCaptor.getValue(), 43);
    verify(jsonConverter).toJson(tenantAttributesForUninstall());
  }

  @Test
  void uninstall_positive_createdWithoutJobIsTreatedAsCompleted() throws IOException, InterruptedException {
    setupHttpClientMock(201);
    when(httpResponse.headers()).thenReturn(headers(Map.of()));
    when(httpResponse.body()).thenReturn("");

    var operation = folioTenantApiClient.uninstall(moduleRequest(true));

    assertThat(operation).isEqualTo(TenantOperation.completed());
    verifyHttpRequest(uninstallRequest(), httpRequestCaptor.getValue(), 43);
    verify(jsonConverter).toJson(tenantAttributesForUninstall());
  }

  @Test
  void uninstallLegacy_positive() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenReturn(httpResponse);
    when(httpResponse.statusCode()).thenReturn(204);

    folioTenantApiClient.uninstallLegacy(moduleRequest(false));

    verifyHttpRequest(uninstallLegacyRequest(), httpRequestCaptor.getValue(), 43);
    verify(jsonConverter).toJson(tenantAttributesForUninstall());
  }

  @Test
  void disableLegacy_positive() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenReturn(httpResponse);
    when(httpResponse.statusCode()).thenReturn(204);

    var operation = folioTenantApiClient.disableLegacy(moduleRequest(false));

    assertThat(operation).isEqualTo(TenantOperation.completed());
    verifyHttpRequest(disableLegacyRequest(), httpRequestCaptor.getValue(), 44);
    verify(jsonConverter).toJson(tenantAttributesForUninstall(false));
  }

  @Test
  void disableLegacy_positive_createdWithJobLocation() throws IOException, InterruptedException {
    setupHttpClientMock(201);
    when(httpResponse.headers()).thenReturn(headers(Map.of(LOCATION, List.of("/_/tenant/" + JOB_ID))));

    var operation = folioTenantApiClient.disableLegacy(moduleRequest(false));

    assertThat(operation).isEqualTo(TenantOperation.accepted(JOB_ID));
    verifyHttpRequest(disableLegacyRequest(), httpRequestCaptor.getValue(), 44);
    verify(jsonConverter).toJson(tenantAttributesForUninstall(false));
  }

  @Test
  void uninstall_negative_invalidResponse() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenReturn(httpResponse);
    when(httpResponse.statusCode()).thenReturn(500);
    when(httpResponse.body()).thenReturn("Failed to install tenant");

    var moduleRequest = moduleRequest(true);
    assertThatThrownBy(() -> folioTenantApiClient.uninstall(moduleRequest))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("Failed to perform doPostTenant call")
      .extracting(e -> ((IntegrationException) e).getCauseHttpStatus()).isEqualTo(500);

    verifyHttpRequest(uninstallRequest(), httpRequestCaptor.getValue(), 43);
    verify(jsonConverter).toJson(tenantAttributesForUninstall());
  }

  @Test
  void uninstall_negative_ioException() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenThrow(IOException.class);

    var moduleRequest = moduleRequest(true);
    assertThatThrownBy(() -> folioTenantApiClient.uninstall(moduleRequest))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("[IOException] Failed to perform request [method: POST, uri: http://mod-test:8081/_/tenant]");

    verifyHttpRequest(uninstallRequest(), httpRequestCaptor.getValue(), 43);
    verify(jsonConverter).toJson(tenantAttributesForUninstall());
  }

  @Test
  void uninstall_negative_interruptedException() throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenThrow(InterruptedException.class);

    var moduleRequest = moduleRequest(true);
    assertThatThrownBy(() -> folioTenantApiClient.uninstall(moduleRequest))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("Request has been interrupted [method: POST, uri: http://mod-test:8081/_/tenant]");

    verifyHttpRequest(uninstallRequest(), httpRequestCaptor.getValue(), 43);
    verify(jsonConverter).toJson(tenantAttributesForUninstall());
  }

  @Test
  void getTenantJob_positive() throws IOException, InterruptedException {
    var body = "{\"id\":\"" + JOB_ID + "\",\"tenant\":\"test\",\"complete\":true,\"error\":\"boom\","
      + "\"messages\":[\"m1\"],\"tenantAttributes\":{\"module_to\":\"mod-test-2.1\"}}";
    setupHttpClientMock(200);
    when(httpResponse.body()).thenReturn(body);

    var job = folioTenantApiClient.getTenantJob(moduleRequest(false), JOB_ID, Duration.ofSeconds(5));

    assertThat(job.getComplete()).isTrue();
    assertThat(job.getError()).isEqualTo("boom");
    assertThat(job.getMessages()).containsExactly("m1");
    var sentRequest = httpRequestCaptor.getValue();
    assertThat(sentRequest.method()).isEqualTo("GET");
    assertThat(sentRequest.uri()).hasToString(jobUri(5000));
    assertThat(sentRequest.headers().firstValue(TENANT)).contains(TENANT_NAME);
    assertThat(sentRequest.timeout()).contains(Duration.ofSeconds(6));
    verify(jsonConverter).fromJson(body, TenantJob.class);
  }

  @Test
  void getTenantJob_negative_notFound() throws IOException, InterruptedException {
    setupHttpClientMock(404);
    when(httpResponse.body()).thenReturn("Tenant not found");
    var moduleRequest = moduleRequest(false);

    assertThatThrownBy(() -> folioTenantApiClient.getTenantJob(moduleRequest, JOB_ID, Duration.ZERO))
      .isInstanceOfSatisfying(IntegrationException.class, error -> {
        assertThat(error).hasMessage("Failed to get tenant job");
        assertThat(error.getCauseHttpStatus()).isEqualTo(404);
        assertThat(error.getErrors()).extracting(Parameter::getValue).containsExactly("404: Tenant not found");
      });
  }

  @Test
  void getTenantJob_negative_malformedBody() throws IOException, InterruptedException {
    setupHttpClientMock(200);
    when(httpResponse.body()).thenReturn("{not-json");
    var moduleRequest = moduleRequest(false);

    assertThatThrownBy(() -> folioTenantApiClient.getTenantJob(moduleRequest, JOB_ID, Duration.ZERO))
      .isInstanceOf(SerializationException.class)
      .hasMessageContaining("Failed to deserialize value");

    verify(jsonConverter).fromJson("{not-json", TenantJob.class);
  }

  @Test
  void getTenantJob_negative_ioException() throws IOException, InterruptedException {
    var ioException = new IOException("reset");
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(any(HttpRequest.class), eq(ofString()))).thenThrow(ioException);
    var moduleRequest = moduleRequest(false);
    var wait = Duration.ofSeconds(1);

    assertThatThrownBy(() -> folioTenantApiClient.getTenantJob(moduleRequest, JOB_ID, wait))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("[IOException] Failed to perform request [method: GET, uri: " + jobUri(1000) + "]")
      .hasCause(ioException);
  }

  private void setupHttpClientMock(int statusCode) throws IOException, InterruptedException {
    when(folioClientConfigurationProperties.getReadTimeout()).thenReturn(Duration.ofSeconds(1));
    when(httpClient.send(httpRequestCaptor.capture(), eq(ofString()))).thenReturn(httpResponse);
    when(httpResponse.statusCode()).thenReturn(statusCode);
  }

  private static String jobUri(long waitMillis) {
    return MODULE_LOCATION + "/_/tenant/" + JOB_ID + "?wait=" + waitMillis;
  }

  private static HttpHeaders headers(Map<String, List<String>> values) {
    return HttpHeaders.of(values, (name, value) -> true);
  }

  private static Stream<String> jobLocationsProvider() {
    return Stream.of(
      "/_/tenant/" + JOB_ID,
      "/_/tenant/" + JOB_ID + "/",
      MODULE_LOCATION + "/_/tenant/" + JOB_ID + "?x=1");
  }

  private static Stream<String> invalidLocationsProvider() {
    return Stream.of(
      "/_/tenant/..",
      "/_/tenant/",
      "/_/tenant/job%20id",
      "/_/tenant/" + JOB_ID + "/extra",
      "/other/" + JOB_ID);
  }

  private static Stream<String> unusableBodiesProvider() {
    return Stream.of("{not-json", "null", "{}", "{\"id\":\"../x\"}");
  }

  private static HttpRequest installRequest() {
    return HttpRequest.newBuilder()
      .uri(URI.create("http://mod-test:8081/_/tenant"))
      .POST(BodyPublishers.ofString(asJsonString(tenantAttributesForInstall())))
      .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
      .header(TENANT, TENANT_NAME)
      .version(Version.HTTP_1_1)
      .build();
  }

  private static HttpRequest uninstallRequest() {
    return HttpRequest.newBuilder()
      .uri(URI.create("http://mod-test:8081/_/tenant"))
      .POST(BodyPublishers.ofString(asJsonString(tenantAttributesForUninstall())))
      .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
      .header(TENANT, TENANT_NAME)
      .version(Version.HTTP_1_1)
      .build();
  }

  private static HttpRequest uninstallLegacyRequest() {
    return HttpRequest.newBuilder()
      .uri(URI.create("http://mod-test:8081/_/tenant"))
      .method("DELETE", BodyPublishers.ofString(asJsonString(tenantAttributesForUninstall())))
      .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
      .header(TENANT, TENANT_NAME)
      .version(Version.HTTP_1_1)
      .build();
  }

  private static HttpRequest disableLegacyRequest() {
    return HttpRequest.newBuilder()
      .uri(URI.create("http://mod-test:8081/_/tenant/disable"))
      .POST(BodyPublishers.ofString(asJsonString(tenantAttributesForUninstall(false))))
      .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
      .header(TENANT, TENANT_NAME)
      .version(Version.HTTP_1_1)
      .build();
  }

  @NonNull
  private static TenantAttributes tenantAttributesForInstall() {
    var tenantAttributes = new TenantAttributes();
    tenantAttributes.setModuleTo(MODULE_ID);
    tenantAttributes.setParameters(List.of(new Parameter().key("loadReference").value("true")));
    return tenantAttributes;
  }

  private static TenantAttributes tenantAttributesForUninstall() {
    return tenantAttributesForUninstall(true);
  }

  private static TenantAttributes tenantAttributesForUninstall(boolean purge) {
    var tenantAttributes = new TenantAttributes();
    tenantAttributes.setModuleFrom(MODULE_ID);
    tenantAttributes.setPurge(purge);
    return tenantAttributes;
  }

  private static void verifyHttpRequest(HttpRequest expectedRequest, HttpRequest httpRequest, long contentLength) {
    assertThat(httpRequest).isEqualTo(expectedRequest);
    assertThat(httpRequest.bodyPublisher()).isPresent()
      .get().extracting(BodyPublisher::contentLength)
      .isEqualTo(contentLength);
  }

  private static ModuleRequest moduleRequest(boolean purge) {
    return moduleRequest(purge, Collections.emptyList());
  }

  private static ModuleRequest moduleRequest(List<Parameter> parameters) {
    return moduleRequest(false, parameters);
  }

  private static ModuleRequest moduleRequest(boolean purge, List<Parameter> parameters) {
    return ModuleRequest.builder()
      .moduleId(MODULE_ID)
      .location(MODULE_LOCATION)
      .tenantId(TENANT_ID)
      .tenantName(TENANT_NAME)
      .purge(purge)
      .tenantParameters(parameters)
      .build();
  }
}
