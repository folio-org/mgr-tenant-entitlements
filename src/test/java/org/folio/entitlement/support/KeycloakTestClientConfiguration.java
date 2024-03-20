package org.folio.entitlement.support;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.support.TestConstants.DUMMY_SSL_CONTEXT;
import static org.folio.test.TestConstants.TENANT_ID;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.keycloak.configuration.properties.KeycloakConfigurationProperties;
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

    private final HttpClient httpClient = buildClient();

    @SneakyThrows
    public static HttpClient buildClient() {
      return HttpClient.newBuilder().sslContext(DUMMY_SSL_CONTEXT).build();
    }

    @SneakyThrows
    public List<String> getAuthorizationResources(String realm) {
      var clientId = getClientId(realm);
      var path = "/admin/realms/{realm}/clients/{clientId}/authz/resource-server/resource";
      var uri = fromUriString(keycloakConfiguration.getUrl() + path)
        .queryParam("first", 0)
        .queryParam("last", 100)
        .buildAndExpand(Map.of("realm", TENANT_ID, "clientId", clientId))
        .encode().toUri();

      var responseJson = sendAndParseGetResponseAsString(uri);
      return StreamSupport.stream(objectMapper.readTree(responseJson).spliterator(), false)
        .map(node -> node.path("name").asText())
        .filter(Objects::nonNull)
        .sorted()
        .collect(toList());
    }

    @SneakyThrows
    public List<HttpMethod> getAuthorizationScopes(String realm) {
      var clientId = getClientId(realm);
      var path = "/admin/realms/{realm}/clients/{clientId}/authz/resource-server/scope";
      var uri = fromUriString(keycloakConfiguration.getUrl() + path)
        .queryParam("first", 0)
        .queryParam("max", 101)
        .buildAndExpand(Map.of("realm", TENANT_ID, "clientId", clientId))
        .encode()
        .toUri();

      var responseJson = sendAndParseGetResponseAsString(uri);
      return StreamSupport.stream(objectMapper.readTree(responseJson).spliterator(), false)
        .map(node -> node.path("name").asText())
        .filter(Objects::nonNull)
        .map(HttpMethod::valueOf)
        .sorted()
        .collect(toList());
    }

    private String sendAndParseGetResponseAsString(URI uri) throws IOException, InterruptedException {
      var request = prepareRequest(uri);
      var response = httpClient.send(request, BodyHandlers.ofString());
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
