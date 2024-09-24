package org.folio.entitlement.integration.keycloak;

import static jakarta.ws.rs.core.Response.Status.BAD_GATEWAY;
import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.folio.entitlement.integration.keycloak.KeycloakServiceTest.KeycloakServiceTestUtils.ALL_SCOPES;
import static org.folio.entitlement.integration.keycloak.KeycloakServiceTest.KeycloakServiceTestUtils.CLIENT_NAME;
import static org.folio.entitlement.integration.keycloak.KeycloakServiceTest.KeycloakServiceTestUtils.CLIENT_SUFFIX;
import static org.folio.entitlement.integration.keycloak.KeycloakServiceTest.KeycloakServiceTestUtils.KEYCLOAK_PROXY_URL;
import static org.folio.entitlement.integration.keycloak.KeycloakServiceTest.KeycloakServiceTestUtils.getScopeId;
import static org.folio.entitlement.integration.keycloak.KeycloakServiceTest.KeycloakServiceTestUtils.keycloakConfigurationProperties;
import static org.folio.entitlement.integration.keycloak.KeycloakServiceTest.KeycloakServiceTestUtils.keycloakMappings;
import static org.folio.entitlement.integration.keycloak.KeycloakServiceTest.KeycloakServiceTestUtils.loginClient;
import static org.folio.entitlement.integration.keycloak.KeycloakServiceTest.KeycloakServiceTestUtils.moduleDescriptor;
import static org.folio.entitlement.integration.keycloak.KeycloakServiceTest.KeycloakServiceTestUtils.parameter;
import static org.folio.entitlement.integration.keycloak.KeycloakServiceTest.KeycloakServiceTestUtils.resource;
import static org.folio.entitlement.integration.keycloak.KeycloakServiceTest.KeycloakServiceTestUtils.scope;
import static org.folio.entitlement.integration.keycloak.KeycloakServiceTest.KeycloakServiceTestUtils.scopeWithId;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.keycloak.configuration.properties.KeycloakConfigurationProperties;
import org.folio.entitlement.integration.keycloak.configuration.properties.KeycloakConfigurationProperties.Login;
import org.folio.entitlement.retry.keycloak.KeycloakRetrySupportService;
import org.folio.entitlement.support.TestUtils;
import org.folio.entitlement.utils.SafeCallable;
import org.folio.security.integration.keycloak.model.KeycloakMappings;
import org.folio.security.integration.keycloak.service.KeycloakModuleDescriptorMapper;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.AuthorizationResource;
import org.keycloak.admin.client.resource.ResourceResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.keycloak.representations.idm.authorization.ResourceServerRepresentation;
import org.keycloak.representations.idm.authorization.ScopeRepresentation;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KeycloakServiceTest {

  @InjectMocks private KeycloakService keycloakService;
  @Mock private KeycloakModuleDescriptorMapper keycloakModuleDescriptorMapper;
  @Spy private final KeycloakConfigurationProperties kcConfiguration = keycloakConfigurationProperties(CLIENT_SUFFIX);

  @Mock private KeycloakRetrySupportService retrySupportService;
  @Mock(answer = RETURNS_DEEP_STUBS) private Keycloak keycloak;
  @Mock(answer = RETURNS_DEEP_STUBS) private AuthorizationResource authorizationResource;

  @BeforeEach
  void setUp() {
    lenient().when(retrySupportService.callWithRetry(any()))
      .thenAnswer(invocation -> invocation.getArgument(0, SafeCallable.class).call());
    lenient().doAnswer(invocation -> {
      invocation.getArgument(0, Runnable.class).run();
      return null;
    }).when(retrySupportService).runWithRetry(any());
  }

  @AfterEach
  void tearDown() {
    clearInvocations(retrySupportService);
    TestUtils.verifyNoMoreInteractions(this);
  }

  private void mockScopesCreation(String... methods) {
    Arrays.stream(methods).forEach(method -> mockScopeCreation(scope(method)));
  }

  private void mockScopeCreation(ScopeRepresentation scope) {
    mockScopeCreation(scope, 201, response -> {});
  }

  private void mockScopeCreation(ScopeRepresentation scope, int status, Consumer<Response> responseCustomizer) {
    var scopeCreationResponse = mock(Response.class);
    when(authorizationResource.scopes().create(scope)).thenReturn(scopeCreationResponse);
    when(scopeCreationResponse.getStatus()).thenReturn(status);
    responseCustomizer.accept(scopeCreationResponse);
  }

  private void mockResourceCreation(ResourceRepresentation resourceRepresentation) {
    mockResourceCreation(resourceRepresentation, 201, response -> {});
  }

  private void mockResourceCreation(ResourceRepresentation resource, int status,
    Consumer<Response> responseCustomizer) {
    var resourceCreationResponse = mock(Response.class);
    when(authorizationResource.resources().create(resource)).thenReturn(resourceCreationResponse);
    when(resourceCreationResponse.getStatus()).thenReturn(status);
    responseCustomizer.accept(resourceCreationResponse);
  }

  @Nested
  @DisplayName("updateAuthResources")
  class UpdateModuleResources {

    @Test
    void positive() {
      var currModuleDescriptor = moduleDescriptor("mod-foo-1.2.0");
      var currMappings = keycloakMappings(resource("/r1", "POST"), resource("/r2", "GET"));

      var prevModuleDescriptor = moduleDescriptor("mod-foo-1.1.0");
      var prevMappings = keycloakMappings(resource("/r1", "POST"), resource("/r3", "PUT"));

      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(List.of(loginClient()));
      when(keycloak.proxy(AuthorizationResource.class, KEYCLOAK_PROXY_URL)).thenReturn(authorizationResource);
      when(authorizationResource.scopes().scopes()).thenReturn(emptyList()).thenReturn(ALL_SCOPES);
      when(keycloakModuleDescriptorMapper.map(currModuleDescriptor, true)).thenReturn(currMappings);
      when(keycloakModuleDescriptorMapper.map(prevModuleDescriptor, true)).thenReturn(prevMappings);

      mockScopesCreation("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE");
      mockResourceCreation(resource("/r2", getScopeId("GET")));

      var r3Resource = resource(randomUUID(), "/r3", scope(getScopeId("PUT"), "PUT"));
      when(authorizationResource.resources().findByName("/r3")).thenReturn(List.of(r3Resource));

      keycloakService.updateAuthResources(prevModuleDescriptor, currModuleDescriptor, TENANT_NAME);

      verify(kcConfiguration, atLeastOnce()).getUrl();
      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
      verify(authorizationResource, atLeastOnce()).scopes();
      verify(authorizationResource, atLeastOnce()).resources();
      verify(authorizationResource.resources().resource(r3Resource.getId())).remove();
    }

    @Test
    void positive_scopeChangedForResource() {
      var currModuleDescriptor = moduleDescriptor("mod-foo-1.2.0");
      var currMappings = keycloakMappings(resource("/r1", "POST", "GET"));

      var prevModuleDescriptor = moduleDescriptor("mod-foo-1.1.0");
      var prevMappings = keycloakMappings(resource("/r1", "POST"));

      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(List.of(loginClient()));
      when(keycloak.proxy(AuthorizationResource.class, KEYCLOAK_PROXY_URL)).thenReturn(authorizationResource);
      when(authorizationResource.scopes().scopes()).thenReturn(ALL_SCOPES);
      when(keycloakModuleDescriptorMapper.map(currModuleDescriptor, true)).thenReturn(currMappings);
      when(keycloakModuleDescriptorMapper.map(prevModuleDescriptor, true)).thenReturn(prevMappings);

      mockResourceCreation(resource(null, "/r1", scopeWithId("POST"), scopeWithId("GET")), 409, resp -> {});

      var r1ResourceId = randomUUID();
      var r1Resource = resource(r1ResourceId, "/r1", scopeWithId("POST"));
      when(authorizationResource.resources().findByName("/r1")).thenReturn(List.of(r1Resource));

      keycloakService.updateAuthResources(prevModuleDescriptor, currModuleDescriptor, TENANT_NAME);

      var updatedR1Resource = resource(r1ResourceId, "/r1", scopeWithId("POST"), scopeWithId("GET"));
      verify(authorizationResource.resources().resource(r1Resource.getId())).update(updatedR1Resource);

      verify(kcConfiguration, atLeastOnce()).getUrl();
      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
      verify(authorizationResource, atLeastOnce()).scopes();
      verify(authorizationResource, atLeastOnce()).resources();
    }

    @Test
    void positive_resourcesUnchangedAndScopesExists() {
      var currModuleDescriptor = moduleDescriptor("mod-foo-1.2.0");
      var currMappings = keycloakMappings(resource("/r1", "POST"), resource("/r2", "GET"));

      var prevModuleDescriptor = moduleDescriptor("mod-foo-1.1.0");
      var prevMappings = keycloakMappings(resource("/r1", "POST"), resource("/r2", "GET"));

      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(List.of(loginClient()));
      when(keycloak.proxy(AuthorizationResource.class, KEYCLOAK_PROXY_URL)).thenReturn(authorizationResource);
      when(authorizationResource.scopes().scopes()).thenReturn(ALL_SCOPES);
      when(keycloakModuleDescriptorMapper.map(currModuleDescriptor, true)).thenReturn(currMappings);
      when(keycloakModuleDescriptorMapper.map(prevModuleDescriptor, true)).thenReturn(prevMappings);

      keycloakService.updateAuthResources(prevModuleDescriptor, currModuleDescriptor, TENANT_NAME);

      verify(kcConfiguration, atLeastOnce()).getUrl();
      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
      verify(authorizationResource, atLeastOnce()).scopes();
    }

    @Test
    void positive_prevDescriptorIsNull() {
      var currModuleDescriptor = moduleDescriptor("mod-foo-1.2.0");
      var currMappings = keycloakMappings(resource("/r1", "POST"), resource("/r2", "GET"));

      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(List.of(loginClient()));
      when(keycloak.proxy(AuthorizationResource.class, KEYCLOAK_PROXY_URL)).thenReturn(authorizationResource);
      when(authorizationResource.scopes().scopes()).thenReturn(ALL_SCOPES);
      when(keycloakModuleDescriptorMapper.map(currModuleDescriptor, true)).thenReturn(currMappings);
      mockResourceCreation(resource(null, "/r1", scopeWithId("POST")));
      mockResourceCreation(resource(null, "/r2", scopeWithId("GET")));

      keycloakService.updateAuthResources(null, currModuleDescriptor, TENANT_NAME);

      verify(kcConfiguration, atLeastOnce()).getUrl();
      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
      verify(authorizationResource, atLeastOnce()).scopes();
      verify(authorizationResource, atLeastOnce()).resources();
    }

    @Test
    void positive_prevDescriptorIsNullAndScopeConflict() {
      var currModuleDescriptor = moduleDescriptor("mod-foo-1.2.0");
      var currMappings = keycloakMappings(resource("/r1", "POST"), resource("/r2", "GET"));

      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(List.of(loginClient()));
      when(keycloak.proxy(AuthorizationResource.class, KEYCLOAK_PROXY_URL)).thenReturn(authorizationResource);
      when(authorizationResource.scopes().scopes()).thenReturn(emptyList()).thenReturn(ALL_SCOPES);
      when(keycloakModuleDescriptorMapper.map(currModuleDescriptor, true)).thenReturn(currMappings);
      mockScopeCreation(scope("GET"), 409, response -> {});
      mockScopesCreation("HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE");
      mockResourceCreation(resource(null, "/r1", scopeWithId("POST")));
      mockResourceCreation(resource(null, "/r2", scopeWithId("GET")));

      keycloakService.updateAuthResources(null, currModuleDescriptor, TENANT_NAME);

      verify(kcConfiguration, atLeastOnce()).getUrl();
      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
      verify(authorizationResource, atLeastOnce()).scopes();
      verify(authorizationResource, atLeastOnce()).resources();
    }

    @Test
    void positive_currentDescriptorIsNull() {
      var prevModuleDescriptor = moduleDescriptor("mod-foo-1.1.0");
      var prevMappings = keycloakMappings(resource("/r1", "POST"), resource("/r2", "GET"));

      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(List.of(loginClient()));
      when(keycloak.proxy(AuthorizationResource.class, KEYCLOAK_PROXY_URL)).thenReturn(authorizationResource);
      when(authorizationResource.scopes().scopes()).thenReturn(ALL_SCOPES);
      when(keycloakModuleDescriptorMapper.map(prevModuleDescriptor, true)).thenReturn(prevMappings);

      var r1Resource = resource(randomUUID(), "/r1", scopeWithId("POST"));
      var r2Resource = resource(randomUUID(), "/r2", scopeWithId("GET"));
      when(authorizationResource.resources().findByName("/r1")).thenReturn(List.of(r1Resource));
      when(authorizationResource.resources().findByName("/r2")).thenReturn(List.of(r2Resource));

      keycloakService.updateAuthResources(prevModuleDescriptor, null, TENANT_NAME);

      verify(kcConfiguration, atLeastOnce()).getUrl();
      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
      verify(authorizationResource, atLeastOnce()).scopes();
      verify(authorizationResource, atLeastOnce()).resources();
      verify(authorizationResource.resources().resource(r1Resource.getId())).remove();
      verify(authorizationResource.resources().resource(r2Resource.getId())).remove();
    }

    @Test
    void negative_clientNotFound() {
      var currentDescriptor = moduleDescriptor("mod-foo-1.2.0");
      var prevDescriptor = moduleDescriptor("mod-foo-1.1.0");

      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(emptyList());

      assertThatThrownBy(() -> keycloakService.updateAuthResources(prevDescriptor, currentDescriptor, TENANT_NAME))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("Client for login operations was not found by name: test-login-client");

      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
    }

    @Test
    void negative_clientFoundByPrefix() {
      var currentDescriptor = moduleDescriptor("mod-foo-1.2.0");
      var prevDescriptor = moduleDescriptor("mod-foo-1.1.0");
      var client = KeycloakServiceTestUtils.client(randomUUID().toString(), CLIENT_NAME + "-test-prefix");

      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(List.of(client));

      assertThatThrownBy(() -> keycloakService.updateAuthResources(prevDescriptor, currentDescriptor, TENANT_NAME))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("Client for login operations was not found by name: test-login-client");

      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
    }

    @Test
    void negative_failedToCreateScopeOnTryCatchClause() {
      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(List.of(loginClient()));
      when(keycloak.proxy(AuthorizationResource.class, KEYCLOAK_PROXY_URL)).thenReturn(authorizationResource);
      when(authorizationResource.scopes().scopes()).thenReturn(emptyList());
      when(authorizationResource.scopes().create(scope("GET"))).thenThrow(new RuntimeException("Error"));
      mockScopesCreation("HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE");

      var currentDescriptor = moduleDescriptor("mod-foo-1.2.0");
      assertThatThrownBy(() -> keycloakService.updateAuthResources(null, currentDescriptor, TENANT_NAME))
        .isInstanceOf(IntegrationException.class)
        .hasMessage("Failed to update authorization scopes in Keycloak")
        .satisfies(error -> assertThat(((IntegrationException) error).getErrors()).containsExactly(
          parameter("scope", "Failed to create scope GET (cause: RuntimeException, causeMessage: Error)")));

      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(kcConfiguration, atLeastOnce()).getUrl();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
      verify(authorizationResource, atLeastOnce()).scopes();
    }

    @Test
    void negative_failedToCreateScopeWithWebApplicationError() {
      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(List.of(loginClient()));
      when(keycloak.proxy(AuthorizationResource.class, KEYCLOAK_PROXY_URL)).thenReturn(authorizationResource);
      when(authorizationResource.scopes().scopes()).thenReturn(emptyList());
      mockScopeCreation(scope("GET"), 502, resp -> when(resp.getStatusInfo()).thenReturn(BAD_GATEWAY));
      mockScopesCreation("HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE");

      var currentDescriptor = moduleDescriptor("mod-foo-1.2.0");
      var prevDescriptor = moduleDescriptor("mod-foo-1.1.0");
      assertThatThrownBy(
        () -> keycloakService.updateAuthResources(prevDescriptor, currentDescriptor, TENANT_NAME)).isInstanceOf(
        IntegrationException.class).hasMessage("Failed to update authorization scopes in Keycloak").satisfies(
          error -> assertThat(((IntegrationException) error).getErrors()).containsExactly(parameter("scope",
            "Failed to create scope GET (cause: WebApplicationException, causeMessage: HTTP 502 Bad Gateway)")));

      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(kcConfiguration, atLeastOnce()).getUrl();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
      verify(authorizationResource, atLeastOnce()).scopes();
    }

    @Test
    void negative_failedToCreateResource() {
      var currentDescriptor = moduleDescriptor("mod-foo-1.2.0");
      var currentMappings = keycloakMappings(resource("/r1", "GET"));

      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(List.of(loginClient()));
      when(keycloak.proxy(AuthorizationResource.class, KEYCLOAK_PROXY_URL)).thenReturn(authorizationResource);
      when(authorizationResource.scopes().scopes()).thenReturn(ALL_SCOPES);
      when(keycloakModuleDescriptorMapper.map(currentDescriptor, true)).thenReturn(currentMappings);

      var resource = resource(null, "/r1", scopeWithId("GET"));
      mockResourceCreation(resource, 502, resp -> when(resp.getStatusInfo()).thenReturn(BAD_GATEWAY));

      assertThatThrownBy(() -> keycloakService.updateAuthResources(null, currentDescriptor, TENANT_NAME)).isInstanceOf(
        IntegrationException.class).hasMessage("Failed to update authorization resources in Keycloak").satisfies(
          error -> assertThat(((IntegrationException) error).getErrors()).containsExactly(parameter("resource",
            "Failed to create resource: /r1 (cause: WebApplicationException, causeMessage: HTTP 502 Bad Gateway)")));

      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(kcConfiguration, atLeastOnce()).getUrl();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
      verify(authorizationResource, atLeastOnce()).scopes();
      verify(authorizationResource, atLeastOnce()).resources();
    }

    @Test
    void negative_failedToCreateResourceOnTryCatchClause() {
      var currentDescriptor = moduleDescriptor("mod-foo-1.2.0");
      var currentMappings = keycloakMappings(resource("/r1", "GET"));

      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(List.of(loginClient()));
      when(keycloak.proxy(AuthorizationResource.class, KEYCLOAK_PROXY_URL)).thenReturn(authorizationResource);
      when(authorizationResource.scopes().scopes()).thenReturn(ALL_SCOPES);
      when(keycloakModuleDescriptorMapper.map(currentDescriptor, true)).thenReturn(currentMappings);

      var resource = resource(null, "/r1", scopeWithId("GET"));
      when(authorizationResource.resources().create(resource)).thenThrow(new RuntimeException("Error"));

      assertThatThrownBy(() -> keycloakService.updateAuthResources(null, currentDescriptor, TENANT_NAME))
        .isInstanceOf(IntegrationException.class)
        .hasMessage("Failed to update authorization resources in Keycloak")
        .satisfies(error -> assertThat(((IntegrationException) error).getErrors()).containsExactly(
          parameter("resource", "Failed to create resource: /r1 (cause: RuntimeException, causeMessage: Error)")));

      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(kcConfiguration, atLeastOnce()).getUrl();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
      verify(authorizationResource, atLeastOnce()).scopes();
      verify(authorizationResource, atLeastOnce()).resources();
    }

    @Test
    void negative_failedToUpdateResource() {
      var currentDescriptor = moduleDescriptor("mod-foo-1.2.0");
      var currentMappings = keycloakMappings(resource("/r1", "GET"));

      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(List.of(loginClient()));
      when(keycloak.proxy(AuthorizationResource.class, KEYCLOAK_PROXY_URL)).thenReturn(authorizationResource);
      when(authorizationResource.scopes().scopes()).thenReturn(ALL_SCOPES);
      when(keycloakModuleDescriptorMapper.map(currentDescriptor, true)).thenReturn(currentMappings);

      var resource = resource(null, "/r1", scopeWithId("GET"));
      mockResourceCreation(resource, 409, resp -> {});

      var foundResourceId = randomUUID();
      var foundResource = resource(foundResourceId, "/r1", scopeWithId("POST"));
      when(authorizationResource.resources().findByName("/r1")).thenReturn(List.of(foundResource));

      var resourceResource = mock(ResourceResource.class);
      var error = new WebApplicationException("Bad Gateway", Status.BAD_GATEWAY);
      when(authorizationResource.resources().resource(foundResourceId.toString())).thenReturn(resourceResource);
      doThrow(error).when(resourceResource).update(resource(foundResourceId, "/r1", scopeWithId("GET")));

      assertThatThrownBy(() -> keycloakService.updateAuthResources(null, currentDescriptor, TENANT_NAME))
        .isInstanceOf(IntegrationException.class)
        .hasMessage("Failed to update authorization resources in Keycloak")
        .satisfies(err -> assertThat(((IntegrationException) err).getErrors()).containsExactly(
          parameter("resource", "Failed to update resource: /r1 (status: 502, causeMessage: Bad Gateway)")));

      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(kcConfiguration, atLeastOnce()).getUrl();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
      verify(authorizationResource, atLeastOnce()).scopes();
      verify(authorizationResource, atLeastOnce()).resources();
    }

    @Test
    void negative_failedToUpdateResourceWhenNotFound() {
      var currentDescriptor = moduleDescriptor("mod-foo-1.2.0");
      var currentMappings = keycloakMappings(resource("/r1", "GET"));

      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(List.of(loginClient()));
      when(keycloak.proxy(AuthorizationResource.class, KEYCLOAK_PROXY_URL)).thenReturn(authorizationResource);
      when(authorizationResource.scopes().scopes()).thenReturn(ALL_SCOPES);
      when(keycloakModuleDescriptorMapper.map(currentDescriptor, true)).thenReturn(currentMappings);

      var resource = resource(null, "/r1", scopeWithId("GET"));
      mockResourceCreation(resource, 409, resp -> {});
      when(authorizationResource.resources().findByName("/r1")).thenReturn(emptyList());

      assertThatThrownBy(() -> keycloakService.updateAuthResources(null, currentDescriptor, TENANT_NAME))
        .isInstanceOf(IntegrationException.class)
        .hasMessage("Failed to update authorization resources in Keycloak")
        .satisfies(error -> assertThat(((IntegrationException) error).getErrors()).containsExactly(
          parameter("resource", "Failed to find created resource by name: /r1")));

      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(kcConfiguration, atLeastOnce()).getUrl();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
      verify(authorizationResource, atLeastOnce()).scopes();
      verify(authorizationResource, atLeastOnce()).resources();
    }
  }

  @Nested
  @DisplayName("removeAuthResources")
  class UnregisterModuleResources {

    @Test
    void positive() {
      var moduleDescriptor = moduleDescriptor("mod-foo-1.1.0");
      var mappings = keycloakMappings(resource("/r1", "POST"));
      var r1Resource = resource(randomUUID(), "/r1", scopeWithId("POST"));

      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(List.of(loginClient()));
      when(keycloak.proxy(AuthorizationResource.class, KEYCLOAK_PROXY_URL)).thenReturn(authorizationResource);
      when(authorizationResource.scopes().scopes()).thenReturn(ALL_SCOPES);
      when(authorizationResource.resources().findByName("/r1")).thenReturn(List.of(r1Resource));
      when(keycloakModuleDescriptorMapper.map(moduleDescriptor, true)).thenReturn(mappings);

      keycloakService.removeAuthResources(moduleDescriptor, TENANT_NAME);

      verify(kcConfiguration, atLeastOnce()).getUrl();
      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
      verify(authorizationResource, atLeastOnce()).scopes();
      verify(authorizationResource, atLeastOnce()).resources();
      verify(authorizationResource.resources().resource(r1Resource.getId())).remove();
    }

    @Test
    void positive_resourceNotFond() {
      var moduleDescriptor = moduleDescriptor("mod-foo-1.1.0");
      var mappings = keycloakMappings(resource("/r1", "POST"));
      var r1Resource = resource(randomUUID(), "/r1", scopeWithId("POST"));

      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(List.of(loginClient()));
      when(keycloak.proxy(AuthorizationResource.class, KEYCLOAK_PROXY_URL)).thenReturn(authorizationResource);
      when(authorizationResource.scopes().scopes()).thenReturn(ALL_SCOPES);
      when(authorizationResource.resources().findByName("/r1")).thenReturn(List.of(r1Resource));
      when(keycloakModuleDescriptorMapper.map(moduleDescriptor, true)).thenReturn(mappings);

      var resourceResource = mock(ResourceResource.class);
      var error = new WebApplicationException("Not Found", Status.NOT_FOUND);
      when(authorizationResource.resources().resource(r1Resource.getId())).thenReturn(resourceResource);
      doThrow(error).when(resourceResource).remove();

      keycloakService.removeAuthResources(moduleDescriptor, TENANT_NAME);

      verify(kcConfiguration, atLeastOnce()).getUrl();
      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
      verify(authorizationResource, atLeastOnce()).scopes();
      verify(authorizationResource, atLeastOnce()).resources();
      verify(authorizationResource.resources().resource(r1Resource.getId())).remove();
    }

    @Test
    void positive_resourceNotFound() {
      var moduleDescriptor = moduleDescriptor("mod-foo-1.1.0");
      var mappings = keycloakMappings(resource("/r1", "POST"));

      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(List.of(loginClient()));
      when(keycloak.proxy(AuthorizationResource.class, KEYCLOAK_PROXY_URL)).thenReturn(authorizationResource);
      when(authorizationResource.scopes().scopes()).thenReturn(ALL_SCOPES);
      when(authorizationResource.resources().findByName("/r1")).thenReturn(emptyList());
      when(keycloakModuleDescriptorMapper.map(moduleDescriptor, true)).thenReturn(mappings);

      keycloakService.removeAuthResources(moduleDescriptor, TENANT_NAME);

      verify(kcConfiguration, atLeastOnce()).getUrl();
      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
      verify(authorizationResource, atLeastOnce()).scopes();
      verify(authorizationResource, atLeastOnce()).resources();
    }

    @Test
    void negative_clientNotFound() {
      var moduleDescriptor = moduleDescriptor("mod-foo-1.1.0");

      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(emptyList());

      assertThatThrownBy(() -> keycloakService.removeAuthResources(moduleDescriptor, TENANT_NAME))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("Client for login operations was not found by name: test-login-client");

      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
    }

    @Test
    void negative_clientFoundByPrefix() {
      var moduleDescriptor = moduleDescriptor("mod-foo-1.1.0");
      var client = KeycloakServiceTestUtils.client(randomUUID().toString(), CLIENT_NAME + "-test-prefix");

      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(List.of(client));

      assertThatThrownBy(() -> keycloakService.removeAuthResources(moduleDescriptor, TENANT_NAME))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("Client for login operations was not found by name: test-login-client");

      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
    }

    @Test
    void negative_failedToDeleteResource() {
      var moduleDescriptor = moduleDescriptor("mod-foo-1.1.0");
      var mappings = keycloakMappings(resource("/r1", "POST"));
      var r1Resource = resource(randomUUID(), "/r1", scopeWithId("POST"));

      when(keycloak.realm(TENANT_NAME).clients().findByClientId(CLIENT_NAME)).thenReturn(List.of(loginClient()));
      when(keycloak.proxy(AuthorizationResource.class, KEYCLOAK_PROXY_URL)).thenReturn(authorizationResource);
      when(keycloakModuleDescriptorMapper.map(moduleDescriptor, true)).thenReturn(mappings);
      when(authorizationResource.scopes().scopes()).thenReturn(ALL_SCOPES);
      when(authorizationResource.resources().findByName("/r1")).thenReturn(List.of(r1Resource));

      var resourceResource = mock(ResourceResource.class);
      var error = new WebApplicationException("Bad Gateway", Status.BAD_GATEWAY);
      when(authorizationResource.resources().resource(r1Resource.getId())).thenReturn(resourceResource);
      doThrow(error).when(resourceResource).remove();

      assertThatThrownBy(() -> keycloakService.removeAuthResources(moduleDescriptor, TENANT_NAME))
        .isInstanceOf(IntegrationException.class)
        .hasMessage("Failed to remove authorization resources in Keycloak")
        .satisfies(err -> assertThat(((IntegrationException) err).getErrors()).containsExactly(
          parameter("resource", "Failed to delete resource: /r1 (status: Bad Gateway, causeMessage: Bad Gateway)")));

      verify(kcConfiguration, atLeastOnce()).getUrl();
      verify(kcConfiguration, atLeastOnce()).getLogin();
      verify(keycloak, atLeastOnce()).realm(TENANT_NAME);
      verify(authorizationResource, atLeastOnce()).scopes();
      verify(authorizationResource, atLeastOnce()).resources();
    }
  }

  static class KeycloakServiceTestUtils {

    static final List<ScopeRepresentation> ALL_SCOPES = List.of(
      scope(randomUUID(), "GET"),
      scope(randomUUID(), "HEAD"),
      scope(randomUUID(), "POST"),
      scope(randomUUID(), "PUT"),
      scope(randomUUID(), "PATCH"),
      scope(randomUUID(), "DELETE"),
      scope(randomUUID(), "OPTIONS"),
      scope(randomUUID(), "TRACE")
    );

    static final String KEYCLOAK_URL = "http://test-keycloak";
    static final String CLIENT_SUFFIX = "-login-client";
    static final String CLIENT_NAME = TENANT_NAME + CLIENT_SUFFIX;
    static final String CLIENT_ID = randomUUID().toString();
    static final URI KEYCLOAK_PROXY_URL = URI.create(String.format(
      "%s/admin/realms/%s/clients/%s/authz/resource-server", KEYCLOAK_URL, TENANT_NAME, CLIENT_ID));

    static String getScopeId(String name) {
      return ALL_SCOPES.stream()
        .filter(scope -> Objects.equals(scope.getName(), name))
        .findFirst()
        .map(ScopeRepresentation::getId)
        .orElse(null);
    }

    static KeycloakConfigurationProperties keycloakConfigurationProperties(String loginClientSuffix) {
      var keycloakConfigurationProperties = new KeycloakConfigurationProperties();
      var login = new Login();
      login.setClientNameSuffix(loginClientSuffix);

      keycloakConfigurationProperties.setLogin(login);
      keycloakConfigurationProperties.setUrl(KEYCLOAK_URL);

      return keycloakConfigurationProperties;
    }

    static ModuleDescriptor moduleDescriptor(String id) {
      return new ModuleDescriptor().id(id);
    }

    static KeycloakMappings keycloakMappings(ResourceRepresentation... resourceRepresentations) {
      return KeycloakMappings.builder().resourceServer(resourceServer(resourceRepresentations)).build();
    }

    static ResourceServerRepresentation resourceServer(ResourceRepresentation... resources) {
      var resourceServerRepresentation = new ResourceServerRepresentation();
      resourceServerRepresentation.setResources(List.of(resources));
      return resourceServerRepresentation;
    }

    static ScopeRepresentation scope(String name) {
      return scope((String) null, name);
    }

    static ScopeRepresentation scope(UUID id, String name) {
      return scope(id != null ? id.toString() : null, name);
    }

    static ScopeRepresentation scope(String id, String name) {
      var scopeRepresentation = new ScopeRepresentation();
      scopeRepresentation.setId(id);
      scopeRepresentation.setName(name);
      return scopeRepresentation;
    }

    static ScopeRepresentation scopeWithId(String name) {
      return scope(getScopeId(name), name);
    }

    static ResourceRepresentation resource(String name, String... scopes) {
      var scopeRepresentationSet = Arrays.stream(scopes)
        .map(KeycloakServiceTestUtils::scope)
        .collect(toSet());

      return resource(null, name, scopeRepresentationSet);
    }

    static ResourceRepresentation resource(UUID id, String name, ScopeRepresentation... scopes) {
      return resource(id, name, Set.of(scopes));
    }

    static ResourceRepresentation resource(UUID id, String name, Set<ScopeRepresentation> scopes) {
      var resourceRepresentation = new ResourceRepresentation();

      resourceRepresentation.setId(id != null ? id.toString() : null);
      resourceRepresentation.setName(name);
      resourceRepresentation.setScopes(scopes);

      return resourceRepresentation;
    }

    static ClientRepresentation loginClient() {
      return client(CLIENT_ID, CLIENT_NAME);
    }

    static ClientRepresentation client(String id, String clientId) {
      var clientRepresentation = new ClientRepresentation();
      clientRepresentation.setId(id);
      clientRepresentation.setName(clientId);
      clientRepresentation.setClientId(clientId);
      return clientRepresentation;
    }

    static Parameter parameter(String key, String value) {
      return new Parameter().key(key).value(value);
    }
  }
}
