package org.folio.entitlement.integration.folio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.time.Duration;
import org.folio.entitlement.configuration.FlowEngineConfigurationProperties;
import org.folio.entitlement.integration.folio.configuration.FolioClientConfigurationProperties;
import org.folio.entitlement.integration.folio.configuration.FolioConfiguration;
import org.folio.entitlement.utils.JsonConverter;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@UnitTest
@ExtendWith(OutputCaptureExtension.class)
class FolioConfigurationTest {

  private final FolioConfiguration folioConfiguration = new FolioConfiguration();

  @Test
  void httpClient_positive() {
    var folioClientConfiguration = mock(FolioClientConfigurationProperties.class);
    when(folioClientConfiguration.getConnectTimeout()).thenReturn(Duration.ofSeconds(1));

    var httpClient = folioConfiguration.httpClient(folioClientConfiguration);

    assertThat(httpClient).isNotNull();
  }

  @Test
  void folioTenantApiClient_positive() {
    var jsonConverter = mock(JsonConverter.class);
    var httpClient = mock(HttpClient.class);
    var clientConfiguration = mock(FolioClientConfigurationProperties.class);

    var folioTenantApiClient = folioConfiguration.folioTenantApiClient(httpClient, jsonConverter, clientConfiguration);

    assertThat(folioTenantApiClient).isNotNull();
  }

  @Test
  void tenantJobPoller_positive(CapturedOutput output) {
    var apiClient = mock(FolioTenantApiClient.class);
    var clientConfiguration = new FolioClientConfigurationProperties();
    var flowEngineConfiguration = new FlowEngineConfigurationProperties();

    var poller = folioConfiguration.tenantJobPoller(apiClient, clientConfiguration, flowEngineConfiguration);

    assertThat(poller).isNotNull();
    assertThat(output.getAll()).doesNotContain("flow execution timeout");
  }

  @Test
  void tenantJobPoller_positive_timeoutEqualToFlowTimeoutIsNotWarned(CapturedOutput output) {
    var apiClient = mock(FolioTenantApiClient.class);
    var clientConfiguration = new FolioClientConfigurationProperties();
    clientConfiguration.getTenantJob().setTimeout(Duration.ofMinutes(10));
    var flowEngineConfiguration = new FlowEngineConfigurationProperties();
    flowEngineConfiguration.setExecutionTimeout(Duration.ofMinutes(10));

    var poller = folioConfiguration.tenantJobPoller(apiClient, clientConfiguration, flowEngineConfiguration);

    assertThat(poller).isNotNull();
    assertThat(output.getAll()).doesNotContain("flow execution timeout");
  }

  @Test
  void tenantJobPoller_positive_timeoutLongerThanFlowTimeoutIsWarned(CapturedOutput output) {
    var apiClient = mock(FolioTenantApiClient.class);
    var clientConfiguration = new FolioClientConfigurationProperties();
    clientConfiguration.getTenantJob().setTimeout(Duration.ofHours(1));
    var flowEngineConfiguration = new FlowEngineConfigurationProperties();

    var poller = folioConfiguration.tenantJobPoller(apiClient, clientConfiguration, flowEngineConfiguration);

    assertThat(poller).isNotNull();
    assertThat(output.getAll())
      .contains("Tenant job timeout (PT1H) is longer than the flow execution timeout (PT30M)");
  }
}
