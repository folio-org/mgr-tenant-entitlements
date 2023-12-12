package org.folio.entitlement.integration.okapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import feign.Contract;
import feign.codec.Decoder;
import feign.codec.Encoder;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OkapiConfigurationTest {

  private final OkapiConfiguration okapiConfiguration = new OkapiConfiguration();

  @Test
  void kongAdminClient_positive() {
    var configuration = mock(OkapiConfigurationProperties.class);
    when(configuration.getUrl()).thenReturn("http://okapi:9130");

    var okapiClient = okapiConfiguration.okapiClient(
      configuration, mock(Contract.class), mock(Encoder.class), mock(Decoder.class));

    assertThat(okapiClient).isNotNull();
  }

  @Test
  void kongGatewayService_positive() {
    var okapiClient = mock(OkapiClient.class);
    var moduleService = mock(EntitlementModuleService.class);
    var okapiModuleInstaller = okapiConfiguration.okapiModuleInstaller(okapiClient, moduleService);
    assertThat(okapiModuleInstaller).isNotNull();
  }
}
