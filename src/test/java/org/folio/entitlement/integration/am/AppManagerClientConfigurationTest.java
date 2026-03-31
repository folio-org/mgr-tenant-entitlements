package org.folio.entitlement.integration.am;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.entitlement.integration.keycloak.KeycloakAdminTokenProvider;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class AppManagerClientConfigurationTest {

  private final AppManagerClientConfiguration appManagerClientConfiguration = new AppManagerClientConfiguration();
  @Mock private KeycloakAdminTokenProvider keycloakAdminTokenProvider;

  @BeforeEach
  void setUp() {
    appManagerClientConfiguration.setUrl("https://test-app-manager.dev");
  }

  @Test
  void applicationManagerClient_positive() {
    var client = appManagerClientConfiguration.applicationManagerClient(keycloakAdminTokenProvider);
    assertThat(client).isNotNull();
  }
}
