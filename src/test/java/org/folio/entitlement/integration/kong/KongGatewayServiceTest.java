package org.folio.entitlement.integration.kong;

import static feign.Request.HttpMethod.GET;
import static feign.Request.HttpMethod.PUT;
import static feign.Request.create;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.common.utils.OkapiHeaders.TENANT;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.module;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.FeignException.InternalServerError;
import feign.FeignException.NotFound;
import feign.RequestTemplate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.common.domain.model.error.Parameter;
import org.folio.common.utils.OkapiHeaders;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.kong.KongAdminClient.KongResultList;
import org.folio.entitlement.integration.kong.model.KongEntityIdentifier;
import org.folio.entitlement.integration.kong.model.KongRoute;
import org.folio.entitlement.integration.kong.model.KongService;
import org.folio.security.domain.model.descriptor.InterfaceDescriptor;
import org.folio.security.domain.model.descriptor.ModuleDescriptor;
import org.folio.security.domain.model.descriptor.RoutingEntry;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KongGatewayServiceTest {

  private static final String MODULE_ID = "test-module-0.0.1";
  private static final String ROUTE_TAGS = TENANT_NAME + "," + MODULE_ID;

  @InjectMocks private KongGatewayService service;
  @Mock private KongAdminClient kongAdminClient;
  @Captor private ArgumentCaptor<KongRoute> routeCaptor;

  @Test
  void addRoutes_positive() {
    var serviceId = UUID.randomUUID().toString();
    when(kongAdminClient.getService(MODULE_ID)).thenReturn(new KongService().id(serviceId).name(MODULE_ID));
    when(kongAdminClient.upsertRoute(eq(serviceId), anyString(), routeCaptor.capture())).then(i -> i.getArgument(2));

    service.addRoutes(TENANT_NAME, applicationDescriptor());

    assertThat(routeCaptor.getAllValues()).hasSize(5).isEqualTo(List.of(
      route(List.of("GET"), "^/entities/([^/]+)$", "test1-2.0"),
      route(List.of("PUT"), "^/entities/([^/]+)/sub-entities$", "test1-2.0"),
      route(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE"),
        "^/entities/sub-entities(.*)$", 0, "test1-2.0", MODULE_ID),
      route(List.of("PUT"), "/tests/1", 1, "test1-2.0", MODULE_ID),
      route(List.of("GET"), "/test2-entities", 1, "test2-1.0", MODULE_ID)));
  }

  @Test
  void addRoutes_positive_interfaceTypeMultiple() {
    var fooModuleId = "mod-foo-1.0.0";
    var fooModuleUuid = UUID.randomUUID().toString();
    var barModuleId = "mod-bar-1.0.0";
    var barModuleUuid = UUID.randomUUID().toString();
    when(kongAdminClient.getService(fooModuleId)).thenReturn(new KongService().id(fooModuleUuid).name(fooModuleId));
    when(kongAdminClient.getService(barModuleId)).thenReturn(new KongService().id(barModuleUuid).name(barModuleId));
    when(kongAdminClient.upsertRoute(anyString(), anyString(), routeCaptor.capture())).then(i -> i.getArgument(2));

    service.addRoutes(TENANT_NAME, applicationDescriptorForMultipleType());

    assertThat(routeCaptor.getAllValues()).hasSize(4).isEqualTo(List.of(
      route(List.of("GET"), "/baz/entities", 1, "baz-multiple-1.0", fooModuleId, getMultipleTypeHeaders(fooModuleId)),
      route(List.of("POST"), "/foo/entities", 1, "foo-1.0", fooModuleId),
      route(List.of("GET"), "/baz/entities", 1, "baz-multiple-1.0", barModuleId, getMultipleTypeHeaders(barModuleId)),
      route(List.of("POST"), "/bar/entities", 1, "bar-1.0", barModuleId)));
  }

  @Test
  void addRoutes_negative_failedToUpsert() {
    var serviceId = UUID.randomUUID().toString();
    var kongRoute = route(List.of("GET"), "^/entities/([^/]+)$", "test-1.0");
    var routeName = kongRoute.getName();
    var request = create(PUT, "/services/" + serviceId + "/" + routeName, emptyMap(), null, (RequestTemplate) null);
    var internalServerError = new InternalServerError("Failed to create route", request, null, emptyMap());

    when(kongAdminClient.getService(MODULE_ID)).thenReturn(new KongService().id(serviceId).name(MODULE_ID));
    when(kongAdminClient.upsertRoute(serviceId, routeName, kongRoute)).thenThrow(internalServerError);

    var routingEntry = new RoutingEntry().methods(List.of("GET")).pathPattern("/entities/{id}");
    var interfaceDesc = new InterfaceDescriptor().id("test").version("1.0").handlers(List.of(routingEntry));
    var moduleDesc = new ModuleDescriptor().id(MODULE_ID).provides(List.of(interfaceDesc));
    var applicationDesc = new ApplicationDescriptor().id(APPLICATION_ID).moduleDescriptors(List.of(moduleDesc));

    assertThatThrownBy(() -> service.addRoutes(TENANT_NAME, applicationDesc))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("Failed to create routes")
      .satisfies(error -> assertThat(((IntegrationException) error).getErrors()).isEqualTo(
        List.of(new Parameter()
          .key("RoutingEntry(methods=[GET], pathPattern=/entities/{id}, path=null)")
          .value("Failed to create route"))));
  }

  @Test
  void addRoutes_negative_serviceNotFound() {
    var request = create(GET, "/services/" + MODULE_ID, emptyMap(), null, (RequestTemplate) null);
    var applicationDescriptor = applicationDescriptor();
    when(kongAdminClient.getService(MODULE_ID)).thenThrow(new NotFound("Not found", request, null, emptyMap()));

    assertThatThrownBy(() -> service.addRoutes(TENANT_NAME, applicationDescriptor))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("Failed to create routes")
      .satisfies(error -> assertThat(((IntegrationException) error).getErrors()).isEqualTo(List.of(
        new Parameter().key("Service is not found: test-module-0.0.1").value("Not found"))));

    verify(kongAdminClient, never()).upsertRoute(anyString(), anyString(), any(KongRoute.class));
  }

  @Test
  void removeRoutes_positive() {
    var serviceId = UUID.randomUUID().toString();
    var kongRoute = new KongRoute().id("routeId").service(KongEntityIdentifier.of(serviceId));
    var routesByTag = new KongResultList<>(null, List.of(kongRoute));
    when(kongAdminClient.getRoutesByTag(ROUTE_TAGS, null)).thenReturn(routesByTag);

    service.removeRoutes(TENANT_NAME, applicationDescriptor());

    verify(kongAdminClient).deleteRoute(serviceId, "routeId");
  }

  @Test
  void removeRoutes_positive_twoPages() {
    var serviceId = UUID.randomUUID().toString();
    var kongRoute1 = new KongRoute().id("routeId1").service(KongEntityIdentifier.of(serviceId));
    var kongRoute2 = new KongRoute().id("routeId2").service(KongEntityIdentifier.of(serviceId));
    var routesByTag1 = new KongResultList<>("test-offset", List.of(kongRoute1));
    var routesByTag2 = new KongResultList<>(null, List.of(kongRoute2));

    when(kongAdminClient.getRoutesByTag(ROUTE_TAGS, null)).thenReturn(routesByTag1);
    when(kongAdminClient.getRoutesByTag(ROUTE_TAGS, "test-offset")).thenReturn(routesByTag2);

    service.removeRoutes(TENANT_NAME, applicationDescriptor());

    verify(kongAdminClient).deleteRoute(serviceId, "routeId1");
    verify(kongAdminClient).deleteRoute(serviceId, "routeId2");
  }

  @Test
  void removeRoutes_negative_failedToFindRoutes() {
    var url = format("/routes?tags=%s,%s", TENANT_NAME, MODULE_ID);
    var request = create(PUT, url, emptyMap(), null, (RequestTemplate) null);
    var internalServerError = new InternalServerError("Unknown error", request, null, emptyMap());
    var applicationDescriptor = applicationDescriptor();
    when(kongAdminClient.getRoutesByTag(ROUTE_TAGS, null)).thenThrow(internalServerError);

    assertThatThrownBy(() -> service.removeRoutes(TENANT_NAME, applicationDescriptor))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("Failed to remove routes")
      .satisfies(error -> assertThat(((IntegrationException) error).getErrors()).isEqualTo(List.of(
        new Parameter().key("Failed to find routes").value("Unknown error"))));
  }

  @Test
  void removeRoutes_negative_failedToDeleteRoutes() {
    var serviceId = UUID.randomUUID().toString();
    var kongRoute = new KongRoute().id("routeId").service(KongEntityIdentifier.of(serviceId));
    var routesByTag = new KongResultList<>(null, List.of(kongRoute));
    when(kongAdminClient.getRoutesByTag(ROUTE_TAGS, null)).thenReturn(routesByTag);

    var url = format("/routes?tags=%s,%s", TENANT_NAME, MODULE_ID);
    var request = create(PUT, url, emptyMap(), null, (RequestTemplate) null);
    var internalServerError = new InternalServerError("Failed to create route", request, null, emptyMap());
    doThrow(internalServerError).when(kongAdminClient).deleteRoute(serviceId, "routeId");

    var applicationDescriptor = applicationDescriptor();
    assertThatThrownBy(() -> service.removeRoutes(TENANT_NAME, applicationDescriptor))
      .isInstanceOf(IntegrationException.class)
      .hasMessage("Failed to remove routes")
      .satisfies(error -> assertThat(((IntegrationException) error).getErrors()).isEqualTo(List.of(
        new Parameter().key("routeId").value("Failed to create route"))));
  }

  private static KongRoute route(List<String> methods, String path, String interfaceId) {
    return route(methods, path, 0, interfaceId, MODULE_ID, singletonMap(TENANT, TENANT_NAME));
  }

  private static KongRoute route(List<String> methods, String path, int priority, String interfaceId, String moduleId) {
    return route(methods, path, priority, interfaceId, moduleId, singletonMap(TENANT, TENANT_NAME));
  }

  private static KongRoute route(List<String> methods, String path, int priority, String interfaceId,
    String moduleId, Map<String, String> headers) {
    var operator = path.endsWith("$") ? "~" : "==";
    var pathExpression = String.format("http.path %s \"%s\"", operator, path);

    var methodsStrBuilder = getMethodsExpression(methods);
    var headersStrBuilder = getHeadersExpression(headers);

    var expression = String.format("(%s && %s && %s)", pathExpression, methodsStrBuilder, headersStrBuilder);

    return new KongRoute()
      .stripPath(false)
      .priority(priority)
      .expression(expression)
      .name(sha1Hex(format("%s|%s|%s|%s|%s", path, join(",", methods), TENANT_NAME, moduleId, interfaceId)))
      .tags(asList(TENANT_NAME, moduleId, interfaceId));
  }

  private static ApplicationDescriptor applicationDescriptor() {
    return new ApplicationDescriptor().id(APPLICATION_ID)
      .moduleDescriptors(List.of(
        new ModuleDescriptor()
          .id(MODULE_ID)
          .provides(List.of(
            new InterfaceDescriptor().id("_tenant").version("1.0").interfaceType("system").handlers(List.of(
              new RoutingEntry().methods(List.of("POST")).pathPattern("/_/tenant"),
              new RoutingEntry().methods(List.of("GET", "DELETE")).pathPattern("/_/tenant/{id}"))),
            new InterfaceDescriptor().id("test1").version("2.0").handlers(List.of(
              new RoutingEntry().methods(List.of("GET")).pathPattern("/entities/{id}"),
              new RoutingEntry().methods(List.of("PUT")).pathPattern("/entities/{id}/sub-entities"),
              new RoutingEntry().methods(List.of("*")).pathPattern("/entities/sub-entities*"),
              new RoutingEntry().methods(List.of("PUT")).path("/tests/1"),
              new RoutingEntry().methods(List.of("GET")))),
            new InterfaceDescriptor().id("test2").version("1.0").handlers(List.of(
              new RoutingEntry().methods(emptyList()).path("/test2"),
              new RoutingEntry().methods(List.of("GET")).path(""),
              new RoutingEntry().methods(List.of("GET")).pathPattern(""),
              new RoutingEntry().methods(List.of("GET")).pathPattern("/test2-entities")))
          ))));
  }

  private static ApplicationDescriptor applicationDescriptorForMultipleType() {
    return new ApplicationDescriptor()
      .id(APPLICATION_ID)
      .modules(List.of(module("mod-foo", "1.0.0"), module("mod-bar", "1.0.0")))
      .moduleDescriptors(List.of(
        new ModuleDescriptor().id("mod-foo-1.0.0").provides(List.of(
          new InterfaceDescriptor().id("baz-multiple").version("1.0").interfaceType("multiple").addHandlersItem(
            new RoutingEntry().methods(List.of("GET")).pathPattern("/baz/entities")),
          new InterfaceDescriptor().id("foo").version("1.0").addHandlersItem(
            new RoutingEntry().methods(List.of("POST")).pathPattern("/foo/entities"))
        )),
        new ModuleDescriptor().id("mod-bar-1.0.0").provides(List.of(
          new InterfaceDescriptor().id("baz-multiple").version("1.0").interfaceType("multiple").addHandlersItem(
            new RoutingEntry().methods(List.of("GET")).pathPattern("/baz/entities")),
          new InterfaceDescriptor().id("bar").version("1.0").addHandlersItem(
            new RoutingEntry().methods(List.of("POST")).pathPattern("/bar/entities"))
        ))));
  }

  private static Map<String, String> getMultipleTypeHeaders(String moduleId) {
    var headers = new LinkedHashMap<String, String>();
    headers.put(TENANT, TENANT_NAME);
    headers.put(OkapiHeaders.MODULE_ID, moduleId);
    return headers;
  }

  private static String getHeadersExpression(Map<String, String> headers) {
    if (headers.size() == 1) {
      var entry = headers.entrySet().iterator().next();
      var updatedHeaderName = entry.getKey().replaceAll("-", "_");
      return "http.headers." + updatedHeaderName + " == \"" + entry.getValue() + "\"";
    }

    return headers.entrySet().stream()
      .map(headersEntry -> getHeadersExpression(Map.ofEntries(headersEntry)))
      .collect(joining(" && ", "(", ")"));
  }

  private static String getMethodsExpression(List<String> methods) {
    if (methods.size() == 1) {
      return "http.method == \"" + methods.get(0) + "\"";
    }

    return methods.stream()
      .map(method -> getMethodsExpression(singletonList(method)))
      .collect(joining(" || ", "(", ")"));
  }
}
