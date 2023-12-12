package org.folio.entitlement.integration.folio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.time.Duration;
import org.folio.entitlement.utils.JsonConverter;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class FolioConfigurationTest {

  private final FolioConfiguration folioConfiguration = new FolioConfiguration();

  @Test
  void httpClient_positive() {
    var folioClientConfiguration = mock(FolioClientConfiguration.class);
    when(folioClientConfiguration.getConnectTimeout()).thenReturn(Duration.ofSeconds(1));

    var httpClient = folioConfiguration.httpClient(folioClientConfiguration);

    assertThat(httpClient).isNotNull();
  }

  @Test
  void folioTenantApiClient_positive() {
    var jsonConverter = mock(JsonConverter.class);
    var httpClient = mock(HttpClient.class);
    var clientConfiguration = mock(FolioClientConfiguration.class);

    var folioTenantApiClient = folioConfiguration.folioTenantApiClient(httpClient, jsonConverter, clientConfiguration);

    assertThat(folioTenantApiClient).isNotNull();
  }
}
