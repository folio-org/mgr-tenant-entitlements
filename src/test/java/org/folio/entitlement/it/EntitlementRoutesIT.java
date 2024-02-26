package org.folio.entitlement.it;

import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_SECOND;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.awaitility.Durations.TWO_HUNDRED_MILLISECONDS;
import static org.folio.common.utils.OkapiHeaders.MODULE_ID;
import static org.folio.common.utils.OkapiHeaders.TENANT;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.it.EntitlementRoutesIT.WiremockResponse.resp;
import static org.folio.entitlement.support.KafkaEventAssertions.assertEntitlementEvents;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.entitlementTopic;
import static org.folio.entitlement.support.TestUtils.asJsonString;
import static org.folio.entitlement.support.TestUtils.parse;
import static org.folio.entitlement.support.TestUtils.readString;
import static org.folio.entitlement.support.TestValues.entitlementRequest;
import static org.folio.entitlement.support.extensions.impl.KongGatewayExtension.KONG_GATEWAY_URL_PROPERTY;
import static org.folio.test.TestConstants.OKAPI_AUTH_TOKEN;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;

import com.fasterxml.jackson.core.type.TypeReference;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.folio.tools.kong.client.KongAdminClient;
import org.folio.tools.kong.model.Service;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@TestMethodOrder(value = MethodOrderer.DisplayName.class)
class EntitlementRoutesIT extends BaseIntegrationTest {

  private static final String APPLICATION_ID = "routes-app-1.0.0";
  private static final String ROUTES_MODULE1_ID = "routes-module1-1.0.0";
  private static final String ROUTES_MODULE2_ID = "routes-module2-2.0.0";
  private static final String UNKNOWN_TENANT_NAME = "unknown";

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @BeforeAll
  static void beforeAll(@Autowired KongAdminClient kongTestAdminClient,
    @Autowired KongAdminClient kongAdminClient, @Autowired MockMvc mockMvc) {
    fakeKafkaConsumer.registerTopic(entitlementTopic(), EntitlementEvent.class);
    var wiremockUrl = "http://host.testcontainers.internal:" + wmAdminClient.getWireMockPort();
    kongTestAdminClient.upsertService(ROUTES_MODULE1_ID, new Service().url(wiremockUrl + "/m1"));
    kongTestAdminClient.upsertService(ROUTES_MODULE2_ID, new Service().url(wiremockUrl + "/m2"));

    installApplication(mockMvc, kongAdminClient);
  }

  @AfterAll
  static void tearDown(@Autowired KongAdminClient kongTestAdminClient,
    @Autowired KongAdminClient kongAdminClient, @Autowired DataSource ds) {
    kongAdminClient.getRoutesByTag(TENANT_NAME, null).forEach(route ->
      kongAdminClient.deleteRoute(route.getService().getId(), route.getId()));

    kongTestAdminClient.deleteService(ROUTES_MODULE1_ID);
    kongTestAdminClient.deleteService(ROUTES_MODULE2_ID);

    var resourceDatabasePopulator = new ResourceDatabasePopulator(new ClassPathResource("/sql/truncate-tables.sql"));
    DatabasePopulatorUtils.execute(resourceDatabasePopulator, ds);
  }

  @MethodSource("validRoutesDataProvider")
  @DisplayName("[1] checkInstalledRoutes_positive_parameterized")
  @WireMockStub("/wiremock/application-routes-it.json")
  @ParameterizedTest(name = "[{index}] method={0}, uri={1}")
  void checkInstalledRoutes_positive_parameterized(HttpMethod method, URI uri, WiremockResponse resp) {
    await().atMost(TEN_SECONDS)
      .pollInterval(ONE_SECOND)
      .untilAsserted(() -> {
        var result = HTTP_CLIENT.send(httpRequest(method, uri, TENANT_NAME), ofString());
        assertThat(result.statusCode()).isEqualTo(OK.value());
        assertThat(parse(result.body(), WiremockResponse.class)).isEqualTo(resp);
      });
  }

  @MethodSource("multipleRoutesDataProvider")
  @DisplayName("[2] checkInstalledRoutes_positive_parameterizedForTypeMultiple")
  @WireMockStub("/wiremock/application-routes-it.json")
  @ParameterizedTest(name = "[{index}] method={0}, uri={1}")
  void checkInstalledRoutes_positive_parameterizedForTypeMultiple(
    HttpMethod method, URI uri, String moduleId, WiremockResponse resp) {
    await().atMost(TEN_SECONDS)
      .pollInterval(TWO_HUNDRED_MILLISECONDS)
      .untilAsserted(() -> {
        var result = HTTP_CLIENT.send(httpRequest(method, uri, TENANT_NAME, moduleId), ofString());
        assertThat(result.statusCode()).isEqualTo(OK.value());
        assertThat(parse(result.body(), WiremockResponse.class)).isEqualTo(resp);
      });
  }

  @MethodSource("invalidRoutesDataProvider")
  @DisplayName("[3] checkInstalledRoutes_negative_parameterized")
  @WireMockStub("/wiremock/application-routes-it.json")
  @ParameterizedTest(name = "[{index}] method={0}, uri={1}")
  void checkInstalledRoutes_negative_parameterized(HttpMethod method, URI uri, String tenantId) throws Exception {
    var result = HTTP_CLIENT.send(httpRequest(method, uri, tenantId), ofString());

    assertThat(result.statusCode()).isEqualTo(NOT_FOUND.value());
    assertThat(readToMap(result.body())).isEqualTo(Map.of("message", "no Route matched with those values"));
  }

  @Test
  @DisplayName("[4] checkInstalledRoutes_positive_routeIsNotFoundForInvalidModuleId")
  void checkInstalledRoutes_positive_routeIsNotFoundForInvalidModuleId() throws Exception {
    var uri = prepareUri("/mult-sample/entities");
    var result = HTTP_CLIENT.send(httpRequest(GET, uri, TENANT_NAME, "unknown-1.0.0"), ofString());

    assertThat(result.statusCode()).isEqualTo(NOT_FOUND.value());
    assertThat(readToMap(result.body())).isEqualTo(Map.of("message", "no Route matched with those values"));
  }

  private static HttpRequest httpRequest(HttpMethod method, URI uri, String tenant) {
    return HttpRequest.newBuilder()
      .uri(uri)
      .method(method.name(), BodyPublishers.noBody())
      .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
      .header(TENANT, tenant)
      .build();
  }

  @SuppressWarnings("SameParameterValue")
  private static HttpRequest httpRequest(HttpMethod method, URI uri, String tenant, String moduleId) {
    return HttpRequest.newBuilder()
      .uri(uri)
      .method(method.name(), BodyPublishers.noBody())
      .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
      .header(TENANT, tenant)
      .header(MODULE_ID, moduleId)
      .build();
  }

  private static Stream<Arguments> validRoutesDataProvider() {
    var uuid = UUID.randomUUID();
    return Stream.of(
      arguments(GET, prepareUri("/foo/entities"), resp(GET, "/m1/foo/entities")),
      arguments(GET, prepareUri("/foo/entities?key=value"), resp(GET, "/m1/foo/entities?key&#x3D;value")),
      arguments(GET, prepareUri("/foo/entities/entries"), resp(GET, "/m1/foo/entities/entries")),
      arguments(GET, prepareUri("/foo/entities/{id}", uuid), resp(GET, "/m1/foo/entities/" + uuid)),

      arguments(POST, prepareUri("/foo/entities"), resp(POST, "/m1/foo/entities")),
      arguments(POST, prepareUri("/foo/{fooId}/sub-entities", uuid), resp(POST, "/m1/foo/" + uuid + "/sub-entities")),
      arguments(PUT, prepareUri("/foo/{id}/entities", uuid), resp(PUT, "/m1/foo/" + uuid + "/entities")),
      arguments(PATCH, prepareUri("/foo/{id}/entities", uuid), resp(PATCH, "/m1/foo/" + uuid + "/entities")),

      arguments(GET, prepareUri("/foo2/values"), resp(GET, "/m1/foo2/values")),
      arguments(GET, prepareUri("/foo2/values/{valueId}", uuid), resp(GET, "/m1/foo2/values/" + uuid)),
      arguments(GET, prepareUri("/foo2/{valueId}/sub-values", uuid), resp(GET, "/m1/foo2/" + uuid + "/sub-values")),

      arguments(GET, prepareUri("/bar/{barId}/baz/{bazId}", uuid, uuid), resp(GET, "/m2/bar/" + uuid + "/baz/" + uuid)),

      arguments(GET, prepareUri("/bar/baz/{bazId}", uuid), resp(GET, "/m2/bar/baz/" + uuid)),
      arguments(GET, prepareUri("/bar/baz/{bazId}/foo", uuid), resp(GET, "/m2/bar/baz/" + uuid + "/foo")),

      arguments(POST, prepareUri("/bar/baz", uuid), resp(POST, "/m2/bar/baz")),
      arguments(GET, prepareUri("/foo/entities/bar", uuid), resp(GET, "/m2/foo/entities/bar")),
      arguments(PUT, prepareUri("/bar/baz/{bazId}", uuid), resp(PUT, "/m2/bar/baz/" + uuid)),
      arguments(PATCH, prepareUri("/bar/baz/{bazId}", uuid), resp(PATCH, "/m2/bar/baz/" + uuid)),
      arguments(DELETE, prepareUri("/bar/baz/{bazId}/foo/{fooId}", uuid, uuid),
        resp(DELETE, "/m2/bar/baz/" + uuid + "/foo/" + uuid))
    );
  }

  private static Stream<Arguments> multipleRoutesDataProvider() {
    return Stream.of(
      arguments(GET, prepareUri("/mult-sample/entities"), ROUTES_MODULE1_ID, resp(GET, "/m1/mult-sample/entities")),
      arguments(GET, prepareUri("/mult-sample/entities"), ROUTES_MODULE2_ID, resp(GET, "/m2/mult-sample/entities"))
    );
  }

  private static Stream<Arguments> invalidRoutesDataProvider() {
    return Stream.of(
      arguments(GET, prepareUri("/"), TENANT_NAME),
      arguments(GET, prepareUri("/unknown-rote"), TENANT_NAME),
      arguments(GET, prepareUri("/foo/entries"), TENANT_NAME),
      arguments(GET, prepareUri("/foo/entities"), UNKNOWN_TENANT_NAME),
      arguments(GET, prepareUri("/foo/entities/{id}/values", UUID.randomUUID()), TENANT_NAME),
      arguments(PUT, prepareUri("/foo/entities"), TENANT_NAME),
      arguments(PUT, prepareUri("/foo/{id}/entities/with-suffix", UUID.randomUUID()), TENANT_NAME),
      arguments(PATCH, prepareUri("/foo/entities"), TENANT_NAME),
      arguments(DELETE, prepareUri("/foo/entities"), TENANT_NAME),

      arguments(GET, prepareUri("/foo/entries?key=value"), TENANT_NAME),
      arguments(POST, prepareUri("/foo/entities/entries"), TENANT_NAME),
      arguments(POST, prepareUri("/foo/entities/"), TENANT_NAME),
      arguments(GET, prepareUri("/mult-sample/entities"), TENANT_NAME),

      arguments(GET, prepareUri("/foo/entities"), UNKNOWN_TENANT_NAME),
      arguments(POST, prepareUri("/foo/entities"), UNKNOWN_TENANT_NAME),
      arguments(GET, prepareUri("/foo2/values"), UNKNOWN_TENANT_NAME),
      arguments(GET, prepareUri("/bar/baz/{bazId}", UUID.randomUUID()), UNKNOWN_TENANT_NAME),
      arguments(GET, prepareUri("/foo/entities/entries"), UNKNOWN_TENANT_NAME),
      arguments(GET, prepareUri("/foo/entities/{id}", UUID.randomUUID()), UNKNOWN_TENANT_NAME)
    );
  }

  @SneakyThrows
  private static void installApplication(MockMvc mockMvc, KongAdminClient kongAdminClient) {
    wmAdminClient.addStubMapping(readString("wiremock/mgr-tenants/test/get.json"));
    wmAdminClient.addStubMapping(readString("wiremock/okapi/_proxy/install-route-app-modules.json"));
    wmAdminClient.addStubMapping(readString("wiremock/mgr-applications/routes-app/get-by-ids-query-full.json"));
    wmAdminClient.addStubMapping(readString("wiremock/mgr-applications/routes-app/get.json"));
    wmAdminClient.addStubMapping(readString("wiremock/mgr-applications/routes-app/get-discovery.json"));
    wmAdminClient.addStubMapping(readString("wiremock/mgr-applications/validate-any-descriptor.json"));

    try {
      mockMvc.perform(post("/entitlements")
          .contentType(APPLICATION_JSON)
          .header(TOKEN, OKAPI_AUTH_TOKEN)
          .content(asJsonString(entitlementRequest(APPLICATION_ID))))
        .andDo(print())
        .andExpect(status().isCreated());

      var routesByTag = kongAdminClient.getRoutesByTag(TENANT_NAME, null);
      assertThat(routesByTag.getData()).hasSize(11);
      assertThat(routesByTag.getOffset()).isNull();

      assertEntitlementEvents(
        List.of(new EntitlementEvent(ENTITLE.name(), ROUTES_MODULE1_ID, TENANT_NAME, TENANT_ID),
          new EntitlementEvent(ENTITLE.name(), ROUTES_MODULE2_ID, TENANT_NAME, TENANT_ID)));

      assertThat(wmAdminClient.unmatchedRequests().getRequests()).isEmpty();
    } finally {
      wmAdminClient.resetAll();
    }
  }

  private static URI prepareUri(String uri, Object... uriVariables) {
    return prepareUri(uri, emptyMap(), uriVariables);
  }

  private static URI prepareUri(String uri, Map<String, String> queryParams, Object... uriVariables) {
    var uriComponentsBuilder = fromUriString(System.getProperty(KONG_GATEWAY_URL_PROPERTY) + uri);
    queryParams.forEach(uriComponentsBuilder::queryParam);
    return uriComponentsBuilder.buildAndExpand(uriVariables).toUri();
  }

  @Nullable
  private static Map<?, ?> readToMap(String responseBodyString) {
    return parse(responseBodyString, new TypeReference<>() {});
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor(staticName = "of")
  static class WiremockResponse {

    private String url;
    private HttpMethod method;

    static WiremockResponse resp(HttpMethod method, String url) {
      return new WiremockResponse(url, method);
    }
  }
}
