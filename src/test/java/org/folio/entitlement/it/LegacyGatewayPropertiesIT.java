package org.folio.entitlement.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.types.IntegrationTest;
import org.folio.tools.kong.configuration.ApiGatewayConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
  "application.kong.connect-timeout=1234",
  "KONG_READ_TIMEOUT=111",
  "APIGW_READ_TIMEOUT=222"
})
class LegacyGatewayPropertiesIT extends BaseIntegrationTest {

  @Autowired private ApplicationContext appContext;
  @Autowired private ApiGatewayConfigurationProperties apiGatewayProperties;

  @Test
  void apiGatewayIntegration_positive_legacyPropertiesAreSupported(CapturedOutput output) {
    assertThat(appContext.containsBean("folioKongAdminClient")).isTrue();
    assertThat(apiGatewayProperties.getConnectTimeout()).isEqualTo(1234);
    assertThat(output.getAll()).contains("Configuration property 'application.kong.connect-timeout' is deprecated and "
      + "will be removed in the Vetch release. Use 'application.apigw.connect-timeout' instead.");
  }

  @Test
  void apiGatewayIntegration_positive_newVariablesTakePrecedenceOverLegacyOnes() {
    assertThat(appContext.containsBean("folioKongGatewayService")).isTrue();
    assertThat(apiGatewayProperties.getReadTimeout()).isEqualTo(222);
  }
}
