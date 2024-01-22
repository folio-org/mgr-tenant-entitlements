package org.folio.entitlement.integration.keycloak;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.getIfEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;

import jakarta.persistence.EntityNotFoundException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Collection;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.keycloak.configuration.properties.KeycloakConfigurationProperties;
import org.folio.security.integration.keycloak.model.KeycloakMappings;
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

  public void registerModuleResources(ModuleDescriptor moduleDescriptor, String realmName) {
    var mappings = moduleDescriptorMapper.map(moduleDescriptor);
    var clientId = getClientId(realmName);
    var resources = prepareListResourcesToCreate(mappings, moduleDescriptor);

    createScopes(clientId, realmName);
    createResources(clientId, realmName, resources);
  }

  public void unregisterModuleResources(ModuleDescriptor moduleDescriptor, String realmName) {
    var mappings = moduleDescriptorMapper.map(moduleDescriptor);
    var clientId = getClientId(realmName);

    removeResources(clientId, realmName, mappings);
  }

  private Collection<ResourceRepresentation> prepareListResourcesToCreate(KeycloakMappings mappings,
    ModuleDescriptor moduleDescriptor) {
    var resources = emptyIfNull(mappings.getResourceServer().getResources());
    var systemResources = emptyIfNull(moduleDescriptor.getProvides()).stream()
      .filter(handler -> isNotEmpty(handler.getInterfaceType()) && handler.getInterfaceType().equals("system"))
      .map(InterfaceDescriptor::getHandlers)
      .flatMap(routingEntries -> emptyIfNull(routingEntries)
        .stream()
        .map(routingEntry -> getIfEmpty(routingEntry.getPath(), routingEntry::getPathPattern)))
      .collect(Collectors.toSet());

    resources.removeIf(res -> systemResources.contains(res.getName()));
    return resources;
  }

  private String getClientId(String realmName) {
    var loginClientId = realmName + properties.getLogin().getClientNameSuffix();
    var loginClients = keycloakClient.realm(realmName).clients().findByClientId(loginClientId);
    var client = loginClients.stream()
      .filter(cl -> cl.getClientId().equals(loginClientId))
      .findFirst();

    if (client.isEmpty()) {
      throw new EntityNotFoundException("Client for login operations was not found. Client name:" + loginClientId);
    }
    return client.get().getId();
  }

  private void createScopes(String clientId, String realmName) {
    var authzClient = getAuthorizationResource(clientId, realmName);
    var existingScopes = authzClient.scopes().scopes()
      .stream()
      .map(ScopeRepresentation::getName)
      .collect(toList());

    stream(HttpMethod.values())
      .map(HttpMethod::name)
      .filter(method -> !existingScopes.contains(method))
      .map(methodName -> {
        var scope = new ScopeRepresentation();
        scope.setName(methodName);
        return scope;
      })
      .forEach(scopeRepresentation -> createScopeIgnoreConflict(authzClient, scopeRepresentation));
  }

  private void createScopeIgnoreConflict(AuthorizationResource client, ScopeRepresentation scope) {
    try (var ignored = client.scopes().create(scope)) {
      log.debug("Scope was created with name: #{}", scope.getName());
    } catch (ClientErrorException exception) {
      if (exception.getResponse().getStatus() != SC_CONFLICT) {
        throw new IntegrationException("Error during creating scopes in Keycloak", exception);
      }
    }
  }

  private void createResources(String clientId, String realmName, Collection<ResourceRepresentation> resources) {
    var authzClient = getAuthorizationResource(clientId, realmName);
    var scopes = authzClient.scopes().scopes()
      .stream().collect(Collectors.toMap(ScopeRepresentation::getName, ScopeRepresentation::getId));

    resources.forEach(resource -> {
      resource.getScopes().forEach(scope -> scope.setId(scopes.get(scope.getName())));
      createResourceIgnoreConflict(authzClient, resource);
    });
  }

  private void createResourceIgnoreConflict(AuthorizationResource client, ResourceRepresentation resource) {
    try (Response ignored = client.resources().create(resource)) {
      log.debug("Resource was created with name: #{}", resource.getName());
    } catch (ClientErrorException exception) {
      if (exception.getResponse().getStatus() != SC_CONFLICT) {
        throw new IntegrationException("Error during creating resources in Keycloak", exception);
      }
    }
  }

  private void removeResources(String clientId, String realmName, KeycloakMappings mappings) {
    var resources = mappings.getResourceServer().getResources();
    var authzClient = getAuthorizationResource(clientId, realmName);

    resources.forEach(resource -> removeResourceIfExist(authzClient, resource));
  }

  private void removeResourceIfExist(AuthorizationResource client, ResourceRepresentation resource) {
    try {
      var searchResourcesResult = client.resources().findByName(resource.getName());
      var existingResource = searchResourcesResult.stream()
        .filter(res -> res.getName().equals(resource.getName()))
        .findFirst();

      if (existingResource.isPresent()) {
        var id = existingResource.get().getId();
        client.resources().resource(id).remove();
      }
    } catch (ClientErrorException exception) {
      if (exception.getResponse().getStatus() != SC_NOT_FOUND) {
        throw new IntegrationException("Error during deleting resources in Keycloak", exception);
      }
    }
  }

  private AuthorizationResource getAuthorizationResource(String clientId, String realmName) {
    return keycloakClient.proxy(AuthorizationResource.class, authorizationResourceUri(clientId, realmName));
  }

  private URI authorizationResourceUri(String clientId, String realmName) {
    return URI.create(
      properties.getUrl() + "/admin/realms/" + realmName + "/clients/" + clientId + "/authz/resource-server");
  }
}


