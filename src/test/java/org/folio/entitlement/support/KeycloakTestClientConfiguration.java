package org.folio.entitlement.support;

import static java.util.Collections.sort;
import static java.util.Collections.unmodifiableList;
import static java.util.Comparator.comparing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.support.TestConstants.HTTP_CLIENT_DUMMY_SSL;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.HEAD;
import static org.springframework.http.HttpMethod.OPTIONS;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpMethod.TRACE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.keycloak.configuration.properties.KeycloakConfigurationProperties;
import org.folio.entitlement.support.model.AuthorizationResource;
import org.keycloak.admin.client.Keycloak;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;

@Log4j2
@TestConfiguration
@RequiredArgsConstructor
public class KeycloakTestClientConfiguration {

  @Bean
  public KeycloakTestClient keycloakTestClient(
    Keycloak keycloak, ObjectMapper objectMapper, KeycloakConfigurationProperties keycloakConfiguration) {
    return new KeycloakTestClient(keycloak, objectMapper, keycloakConfiguration);
  }

  @RequiredArgsConstructor
  public static final class KeycloakTestClient {

    public static final List<HttpMethod> ALL_HTTP_METHODS =
      List.of(DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT, TRACE);

    private final Keycloak keycloak;
    private final ObjectMapper objectMapper;
    private final KeycloakConfigurationProperties keycloakConfiguration;

    @SneakyThrows
    public List<AuthorizationResource> getAuthorizationResources(String realm) {
      var clientId = getClientId(realm);
      var path = "/admin/realms/{realm}/clients/{clientId}/authz/resource-server/resource";
      var uri = fromUriString(keycloakConfiguration.getUrl() + path)
        .queryParam("first", 0)
        .queryParam("last", 100)
        .buildAndExpand(Map.of("realm", realm, "clientId", clientId))
        .encode().toUri();

      var responseJson = sendAndParseGetResponseAsString(uri);
      return StreamSupport.stream(objectMapper.readTree(responseJson).spliterator(), false)
        .map(KeycloakTestClient::toAuthorizationResource)
        .sorted(comparing(AuthorizationResource::name))
        .toList();
    }

    private static AuthorizationResource toAuthorizationResource(JsonNode node) {
      var resourceName = node.path("name").asText();

      var scopeNode = node.path("scopes");
      var resultScopes = new ArrayList<String>();
      if (scopeNode instanceof ArrayNode scopes) {
        for (var scope : scopes) {
          var scopeName = scope.path("name").asText();
          if (scopeName != null) {
            resultScopes.add(scopeName);
          }
        }
      }

      sort(resultScopes);
      return new AuthorizationResource(resourceName, unmodifiableList(resultScopes));
    }

    @SneakyThrows
    public List<HttpMethod> getAuthorizationScopes(String realm) {
      var clientId = getClientId(realm);
      var path = "/admin/realms/{realm}/clients/{clientId}/authz/resource-server/scope";
      var uri = fromUriString(keycloakConfiguration.getUrl() + path)
        .queryParam("first", 0)
        .queryParam("max", 101)
        .buildAndExpand(Map.of("realm", realm, "clientId", clientId))
        .encode()
        .toUri();

      var responseJson = sendAndParseGetResponseAsString(uri);
      return StreamSupport.stream(objectMapper.readTree(responseJson).spliterator(), false)
        .map(node -> node.path("name").asText())
        .filter(Objects::nonNull)
        .map(HttpMethod::valueOf)
        .sorted()
        .toList();
    }

    private String sendAndParseGetResponseAsString(URI uri) throws IOException, InterruptedException {
      var request = prepareRequest(uri);
      var response = HTTP_CLIENT_DUMMY_SSL.send(request, BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      return response.body();
    }

    private HttpRequest prepareRequest(URI uri) {
      return HttpRequest.newBuilder(uri)
        .GET()
        .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
        .header(AUTHORIZATION, "Bearer " + keycloak.tokenManager().getAccessTokenString())
        .build();
    }

    private String getClientId(String realmName) {
      var loginClientId = realmName + keycloakConfiguration.getLogin().getClientNameSuffix();
      var loginClients = keycloak.realm(realmName).clients().findByClientId(loginClientId);
      var client = loginClients.stream()
        .filter(cl -> cl.getClientId().equals(loginClientId))
        .findFirst();

      assertThat(client).isPresent();
      return client.get().getId();
    }
  }
}
