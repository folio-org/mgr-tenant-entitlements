package org.folio.entitlement.integration.keycloak;

import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.common.utils.Collectors.toLinkedHashMap;
import static org.folio.entitlement.utils.EntitlementServiceUtils.filterAndMap;

import jakarta.persistence.EntityNotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.keycloak.configuration.properties.KeycloakConfigurationProperties;
import org.folio.entitlement.retry.keycloak.KeycloakRetrySupportService;
import org.folio.security.integration.keycloak.service.KeycloakModuleDescriptorMapper;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.AuthorizationResource;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.keycloak.representations.idm.authorization.ScopeRepresentation;
import org.springframework.http.HttpMethod;

@Log4j2
@RequiredArgsConstructor
public class KeycloakService {

  private final Keycloak keycloakClient;
  private final KeycloakModuleDescriptorMapper moduleDescriptorMapper;
  private final KeycloakConfigurationProperties properties;
  private final KeycloakRetrySupportService retrySupport;

  /**
   * Registers authorization resources and scopes in Keycloak.
   *
   * @param prevDescriptor - previous module descriptor (if upgrade), nullable
   * @param newDescriptor - new module descriptor, must be not-null
   * @param realm - Keycloak realm name
   */
  public void updateAuthResources(ModuleDescriptor prevDescriptor, ModuleDescriptor newDescriptor, String realm) {
    var authResourceClient = getAuthorizationClient(realm);
    var scopeErrorParameters = createScopes(authResourceClient);
    if (isNotEmpty(scopeErrorParameters)) {
      throw new IntegrationException("Failed to update authorization scopes in Keycloak", scopeErrorParameters);
    }

    KeycloakUtils.addPubSubResources(newDescriptor);

    var newAuthResources = getAuthorizationResources(newDescriptor);
    var prevAuthResources = getAuthorizationResources(prevDescriptor);

    var newResources = filterAndMap(newAuthResources.entrySet(),
      not(entry -> resourceAlreadyExists(entry.getKey(), entry.getValue(), prevAuthResources)),
      Entry::getValue);

    var deprecatedResources = filterAndMap(prevAuthResources.keySet(),
      not(newAuthResources::containsKey), prevAuthResources::get);

    var resourceErrorParameters = new ArrayList<Parameter>();
    resourceErrorParameters.addAll(createResources(authResourceClient, newResources));
    resourceErrorParameters.addAll(removeResources(authResourceClient, deprecatedResources));

    if (isNotEmpty(resourceErrorParameters)) {
      throw new IntegrationException("Failed to update authorization resources in Keycloak", resourceErrorParameters);
    }
  }

  /**
   * Removes authorization resources from Keycloak.
   *
   * @param moduleDescriptor - previous module descriptor (if upgrade), nullable
   * @param realmName - Keycloak realm name
   */
  public void removeAuthResources(ModuleDescriptor moduleDescriptor, String realmName) {
    var authResourceClient = getAuthorizationClient(realmName);
    var mappings = moduleDescriptorMapper.map(moduleDescriptor, true);
    var resources = mappings.getResourceServer().getResources();

    var removeResourcesErrors = removeResources(authResourceClient, resources);
    if (isNotEmpty(removeResourcesErrors)) {
      throw new IntegrationException("Failed to remove authorization resources in Keycloak", removeResourcesErrors);
    }
  }

  private static boolean resourceAlreadyExists(String resourceName, ResourceRepresentation resource,
    Map<String, ResourceRepresentation> prevAuthResources) {
    var prevResource = prevAuthResources.get(resourceName);
    return prevAuthResources.containsKey(resourceName) && Objects.equals(getScopes(resource), getScopes(prevResource));
  }

  private static Set<String> getScopes(ResourceRepresentation resourceRepresentation) {
    return toStream(resourceRepresentation.getScopes()).map(ScopeRepresentation::getName).collect(toSet());
  }

  private Map<String, ResourceRepresentation> getAuthorizationResources(ModuleDescriptor moduleDescriptor) {
    if (moduleDescriptor == null) {
      return emptyMap();
    }

    var mappings = moduleDescriptorMapper.map(moduleDescriptor, true);
    var resources = mappings.getResourceServer().getResources();
    return groupResourcesByName(resources);
  }

  private static Map<String, ResourceRepresentation> groupResourcesByName(List<ResourceRepresentation> resources) {
    return resources.stream().collect(toLinkedHashMap(ResourceRepresentation::getName));
  }

  private AuthorizationResource getAuthorizationClient(String realmName) {
    var name = realmName + properties.getLogin().getClientNameSuffix();
    var loginClients =
      retrySupport.callWithRetry(() -> keycloakClient.realm(realmName).clients().findByClientId(name));
    var loginClientId = loginClients.stream()
      .filter(cl -> cl.getClientId().equals(name))
      .findFirst()
      .orElseThrow(() -> new EntityNotFoundException("Client for login operations was not found by name: " + name))
      .getId();

    return getAuthorizationResourceClient(loginClientId, realmName);
  }

  private List<Parameter> createScopes(AuthorizationResource client) {
    var existingScopes = getAuthorizationScopes(client);

    return stream(HttpMethod.values())
      .map(HttpMethod::name)
      .filter(method -> !existingScopes.containsKey(method))
      .map(KeycloakService::mapToScopeRepresentation)
      .map(scopeRepresentation -> createScopeIgnoreConflict(client, scopeRepresentation))
      .flatMap(Optional::stream)
      .toList();
  }

  private static ScopeRepresentation mapToScopeRepresentation(String methodName) {
    var scope = new ScopeRepresentation();
    scope.setName(methodName);
    return scope;
  }

  private Optional<Parameter> createScopeIgnoreConflict(AuthorizationResource client, ScopeRepresentation scope) {
    var scopeName = scope.getName();
    try (var response = retrySupport.callWithRetry(() -> failOnInternalServerError(client.scopes().create(scope)))) {
      var status = response.getStatus();
      if (status >= SC_BAD_REQUEST && status != SC_CONFLICT) {
        return Optional.of(getScopeError(format(
          "Failed to create scope %s (status: %s, info: %s)", scope.getName(), status, response.getStatusInfo())));
      }

      log.debug("Scope was created with name: #{}", scopeName);
    } catch (Exception exception) {
      return Optional.of(getScopeError(format("Failed to create scope %s (cause: %s, causeMessage: %s)",
        scopeName, exception.getClass().getSimpleName(), exception.getMessage())));
    }

    return Optional.empty();
  }

  private List<Parameter> createResources(AuthorizationResource client,
    Collection<ResourceRepresentation> resources) {
    var scopes = getAuthorizationScopes(client);

    return resources.stream()
      .map(resourceRepresentation -> withScopeIds(resourceRepresentation, scopes))
      .map(resource -> createResourceSafe(client, resource))
      .flatMap(Optional::stream)
      .toList();
  }

  private Map<String, String> getAuthorizationScopes(AuthorizationResource authResourceClient) {
    return toStream(retrySupport.callWithRetry(() -> authResourceClient.scopes().scopes()))
      .collect(toMap(ScopeRepresentation::getName, ScopeRepresentation::getId, (o1, o2) -> o1));
  }

  private Optional<Parameter> createResourceSafe(AuthorizationResource client, ResourceRepresentation resource) {
    var name = resource.getName();
    try (
      var response = retrySupport.callWithRetry(() -> failOnInternalServerError(client.resources().create(resource)))) {
      var status = response.getStatus();
      if (status == SC_CONFLICT) {
        return updateResource(client, resource);
      }

      if (status >= SC_BAD_REQUEST) {
        var statusInfo = response.getStatusInfo();
        var errorMessage = format("Failed to create resource: %s (status: %s, info: %s)", name, status, statusInfo);
        return Optional.of(getResourceError(errorMessage));
      }

      log.debug("Resource was created with name: #{}", name);
    } catch (Exception exception) {
      return Optional.of(getResourceError(format("Failed to create resource: %s (cause: %s, causeMessage: %s)",
        name, exception.getClass().getSimpleName(), exception.getMessage())));
    }

    return Optional.empty();
  }

  private Optional<Parameter> updateResource(AuthorizationResource client, ResourceRepresentation resource) {
    var name = resource.getName();
    var resourceByName = findResourceByName(client, name);
    if (resourceByName.isEmpty()) {
      return Optional.of(getResourceError("Failed to find created resource by name: " + name));
    }

    var resourceRepresentation = resourceByName.get();
    var resourceId = resourceRepresentation.getId();
    resourceRepresentation.setScopes(resource.getScopes());

    try {
      retrySupport.runWithRetry(() -> client.resources().resource(resourceId).update(resourceRepresentation));
      log.debug("Authorization scopes are updated for resource: name = {}", name);
      return Optional.empty();
    } catch (WebApplicationException exception) {
      var param = getResourceError(format("Failed to update resource: %s (status: %s, causeMessage: %s)",
        name, exception.getResponse().getStatus(), exception.getMessage()));
      return Optional.of(param);
    }
  }

  private static ResourceRepresentation withScopeIds(ResourceRepresentation resource, Map<String, String> scopes) {
    resource.getScopes().forEach(scope -> scope.setId(scopes.get(scope.getName())));
    return resource;
  }

  private List<Parameter> removeResources(AuthorizationResource authResourceClient,
    List<ResourceRepresentation> resources) {
    return resources.stream()
      .map(resource -> removeResourceIfExist(authResourceClient, resource))
      .flatMap(Optional::stream)
      .toList();
  }

  private Optional<Parameter> removeResourceIfExist(AuthorizationResource client,
    ResourceRepresentation resource) {
    var resourceName = resource.getName();
    try {
      var existingResource = findResourceByName(client, resourceName);
      if (existingResource.isPresent()) {
        var id = existingResource.get().getId();
        log.debug("Keycloak resource removed: id = {}, name = {}", id, resourceName);
        retrySupport.runWithRetry(() -> client.resources().resource(id).remove());
      }
    } catch (WebApplicationException exception) {
      var response = exception.getResponse();
      if (response.getStatus() != SC_NOT_FOUND) {
        var value = format("Failed to delete resource: %s (status: %s, causeMessage: %s)",
          resourceName, response.getStatusInfo(), exception.getMessage());
        return Optional.of(getResourceError(value));
      }
    }

    return Optional.empty();
  }

  private Optional<ResourceRepresentation> findResourceByName(AuthorizationResource client, String name) {
    var searchResourcesResult = retrySupport.callWithRetry(() -> client.resources().findByName(name));
    return searchResourcesResult.stream()
      .filter(res -> res.getName().equals(name))
      .findFirst();
  }

  private AuthorizationResource getAuthorizationResourceClient(String clientId, String realmName) {
    return retrySupport.callWithRetry(
      () -> keycloakClient.proxy(AuthorizationResource.class, authorizationResourceUri(clientId, realmName)));
  }

  private URI authorizationResourceUri(String clientId, String realmName) {
    return URI.create(
      properties.getUrl() + "/admin/realms/" + realmName + "/clients/" + clientId + "/authz/resource-server");
  }

  private static Parameter getResourceError(String message) {
    return new Parameter().key("resource").value(message);
  }

  private static Parameter getScopeError(String message) {
    return new Parameter().key("scope").value(message);
  }

  private static Response failOnInternalServerError(Response response) {
    if (response.getStatus() >= 500) {
      response.close(); // Release HTTP connection
      throw new WebApplicationException(response);
    }
    return response;
  }
}
