package org.folio.entitlement.integration.okapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.folio.entitlement.integration.okapi.configuration.OkapiConfiguration;
import org.folio.entitlement.integration.okapi.configuration.OkapiConfigurationProperties;
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
  void okapiClient_positive() {
    var configuration = mock(OkapiConfigurationProperties.class);
    when(configuration.getUrl()).thenReturn("http://okapi:9130");

    var okapiClient = okapiConfiguration.okapiClient(configuration);

    assertThat(okapiClient).isNotNull();
  }

  @Test
  void okapiModuleInstaller_positive() {
    var okapiClient = mock(OkapiClient.class);
    var moduleService = mock(EntitlementModuleService.class);
    var okapiModuleInstaller = okapiConfiguration.okapiModuleInstaller(okapiClient, moduleService);
    assertThat(okapiModuleInstaller).isNotNull();
  }
}
