package org.folio.entitlement.integration.kong;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.OkapiHeaders.MODULE_ID;
import static org.folio.common.utils.OkapiHeaders.TENANT;
import static org.folio.entitlement.integration.kong.model.expression.RouteExpressions.combineUsingAnd;
import static org.folio.entitlement.integration.kong.model.expression.RouteExpressions.combineUsingOr;
import static org.folio.entitlement.integration.kong.model.expression.RouteExpressions.httpHeader;
import static org.folio.entitlement.integration.kong.model.expression.RouteExpressions.httpMethod;
import static org.folio.entitlement.integration.kong.model.expression.RouteExpressions.httpPath;
import static org.folio.entitlement.utils.RoutingEntryUtils.getMethods;

import feign.FeignException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.kong.KongAdminClient.KongResultList;
import org.folio.entitlement.integration.kong.model.KongRoute;
import org.folio.entitlement.integration.kong.model.expression.RouteExpression;
import org.folio.security.domain.model.descriptor.InterfaceDescriptor;
import org.folio.security.domain.model.descriptor.ModuleDescriptor;
import org.folio.security.domain.model.descriptor.RoutingEntry;

@Log4j2
@RequiredArgsConstructor
public class KongGatewayService {

  private static final String MULTIPLE_INTERFACE_TYPE = "multiple";
  private static final String KONG_PATH_VARIABLE_REGEX_GROUP = "([^/]+)";
  private static final Pattern PATH_VARIABLE_REGEX = Pattern.compile("\\{[^}]+}");

  private final KongAdminClient kongAdminClient;

  /**
   * Adds tenant routes for API Gateway.
   *
   * @param tenantName - tenant name as {@link String}
   * @param applicationDescriptor - installed application descriptor
   * @throws IntegrationException if any of route create requests failed
   */
  public void addRoutes(String tenantName, ApplicationDescriptor applicationDescriptor) {
    var errors = new ArrayList<Parameter>();
    for (var moduleDescriptor : emptyIfNull(applicationDescriptor.getModuleDescriptors())) {
      errors.addAll(addRoutesForModule(moduleDescriptor, tenantName));
    }

    if (isNotEmpty(errors)) {
      throw new IntegrationException("Failed to create routes", errors);
    }
  }

  /**
   * Removes routes for tenant from API Gateway.
   *
   * @param tenantName - tenant name as {@link String}
   * @throws IntegrationException if any of route delete requests failed
   */
  public void removeRoutes(String tenantName, ApplicationDescriptor applicationDescriptor) {
    var allErrors = new ArrayList<Parameter>();
    for (ModuleDescriptor module : applicationDescriptor.getModuleDescriptors()) {
      allErrors.addAll(removeKongRoutes(tenantName, module.getId()));
    }
    if (isNotEmpty(allErrors)) {
      throw new IntegrationException("Failed to remove routes", allErrors);
    }
  }

  private List<Parameter> addRoutesForModule(ModuleDescriptor moduleDescriptor, String tenantId) {
    var failedRoutingEntries = new ArrayList<Parameter>();
    var moduleId = moduleDescriptor.getId();

    String serviceId;
    try {
      serviceId = kongAdminClient.getService(moduleId).getId();
    } catch (Exception exception) {
      return singletonList(new Parameter().key("Service is not found: " + moduleId).value(exception.getMessage()));
    }

    for (var interfaceDescriptor : emptyIfNull(moduleDescriptor.getProvides())) {
      failedRoutingEntries.addAll(createRoutes(interfaceDescriptor, serviceId, moduleId, tenantId));
    }

    return failedRoutingEntries;
  }

  private List<Parameter> createRoutes(InterfaceDescriptor desc, String serviceId, String moduleId, String tenantId) {
    var interfaceId = desc.getId() + "-" + desc.getVersion();
    if (desc.isSystem()) {
      log.debug("System interface is ignored [tenantId={}, moduleId={}, serviceId={}, interfaceId={}]",
        tenantId, moduleId, serviceId, interfaceId);
      return emptyList();
    }

    var interfaceType = desc.getInterfaceType();
    var isMultiple = StringUtils.equals(interfaceType, MULTIPLE_INTERFACE_TYPE);
    return emptyIfNull(desc.getHandlers()).stream()
      .map(routingEntry -> getRoute(tenantId, moduleId, interfaceId, routingEntry, isMultiple)
        .flatMap(kongRoute -> createKongRoute(serviceId, kongRoute, routingEntry)))
      .flatMap(Optional::stream)
      .collect(toList());
  }

  private Optional<Parameter> createKongRoute(String serviceId, KongRoute route, RoutingEntry re) {
    try {
      kongAdminClient.upsertRoute(serviceId, route.getName(), route);
      return Optional.empty();
    } catch (FeignException exception) {
      return Optional.of(new Parameter().key(asString(re)).value(exception.getMessage()));
    }
  }

  private List<Parameter> removeKongRoutes(String tenantId, String moduleId) {
    var errorParameters = new ArrayList<Parameter>();
    String offset = null;
    do {
      try {
        var routes = kongAdminClient.getRoutesByTag(String.join(",", tenantId, moduleId), offset);
        errorParameters.addAll(deleteRoutePage(routes));
        offset = routes.getOffset();
      } catch (Exception exception) {
        errorParameters.add(new Parameter().key("Failed to find routes").value(exception.getMessage()));
        offset = null;
      }
    } while (offset != null);
    return errorParameters;
  }

  private List<Parameter> deleteRoutePage(KongResultList<KongRoute> routes) {
    var errorParameters = new ArrayList<Parameter>();
    for (var route : routes) {
      try {
        var routeId = route.getId();
        kongAdminClient.deleteRoute(route.getService().getId(), routeId);
      } catch (Exception exception) {
        errorParameters.add(new Parameter().key(route.getId()).value(exception.getMessage()));
      }
    }

    return errorParameters;
  }

  private static Optional<KongRoute> getRoute(
    String tenantId, String moduleId, String interfaceId, RoutingEntry re, boolean isMultiple) {

    var staticPath = re.getStaticPath();
    var httpMethods = getMethods(re);
    if (StringUtils.isEmpty(staticPath) || CollectionUtils.isEmpty(httpMethods)) {
      log.debug("Route cannot be created [moduleId={}, tenantId={}, interfaceId={}, routingEntry={}]",
        () -> moduleId, () -> tenantId, () -> interfaceId, () -> asString(re));
      return Optional.empty();
    }

    var kongPathPair = updatePathPatternForKongGateway(staticPath);
    var path = kongPathPair.getLeft();
    var routeNameBuilder = new StringJoiner("|")
      .add(path)
      .add(String.join(",", httpMethods))
      .add(tenantId)
      .add(moduleId)
      .add(interfaceId);

    var pathExpression = path.endsWith("$") ? httpPath().regexMatching(path) : httpPath().equalsTo(path);
    var methodsExpression = combineUsingOr(mapItems(httpMethods, method -> httpMethod().equalsTo(method)));
    var headersExpression = getHeadersExpression(tenantId, moduleId, isMultiple);

    return Optional.of(
      new KongRoute()
        .priority(kongPathPair.getRight())
        .name(sha1Hex(routeNameBuilder.toString()))
        .expression(combineUsingAnd(pathExpression, methodsExpression, headersExpression))
        .tags(List.of(tenantId, moduleId, interfaceId))
        .stripPath(false)
    );
  }

  private static RouteExpression getHeadersExpression(String tenantId, String moduleId, boolean isMultiple) {
    return isMultiple
      ? combineUsingAnd(httpHeader(TENANT).equalsTo(tenantId), httpHeader(MODULE_ID).equalsTo(moduleId))
      : httpHeader(TENANT).equalsTo(tenantId);
  }

  /**
   * Kong starting from version 3 handles request using expressions, but doing it without an exact match on regex, so
   * each pattern should start with '^' symbol.
   *
   * @param staticPath - request path
   * @return pair of updated path and its priority
   */
  private static Pair<String, Integer> updatePathPatternForKongGateway(String staticPath) {
    if (StringUtils.containsAny(staticPath, '{', '}', '*')) {
      var pathRegex = PATH_VARIABLE_REGEX.matcher(staticPath)
        .replaceAll(KONG_PATH_VARIABLE_REGEX_GROUP)
        .replace("*", "(.*)")
        + "$";
      return Pair.of("^" + pathRegex, 0);
    }

    return Pair.of(staticPath, 1);
  }

  private static String asString(Object re) {
    return re.toString().replace("\n", "\\n");
  }
}
