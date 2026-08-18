package org.folio.entitlement.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.utils.LogTestUtil.captureLog4J2Logs;
import static org.folio.entitlement.utils.LogTestUtil.stopCaptureLog4J2Logs;

import java.util.List;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.types.IntegrationTest;
import org.folio.tools.kong.configuration.ApiGatewayConfigurationProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@ContextConfiguration(initializers = LegacyGatewayPropertiesIT.StartupLogCapturer.class)
@TestPropertySource(properties = {
  "application.kong.enabled=true",
  "application.kong.register-module=false",
  "application.kong.connect-timeout=1234",
  "KONG_INTEGRATION_ENABLED=false",
  "APIGW_ENABLED=true",
  "KONG_READ_TIMEOUT=111",
  "APIGW_READ_TIMEOUT=222"
})
class LegacyGatewayPropertiesIT extends BaseIntegrationTest {

  private static List<String> startupLogs;

  @Autowired private ApplicationContext appContext;
  @Autowired private ApiGatewayConfigurationProperties apiGatewayProperties;

  @AfterAll
  static void stopLogCapture() {
    stopCaptureLog4J2Logs();
  }

  @Test
  void apiGatewayIntegration_positive_legacyPropertiesAreSupported() {
    assertThat(appContext.containsBean("folioKongAdminClient")).isTrue();
    assertThat(apiGatewayProperties.getConnectTimeout()).isEqualTo(1234);
    assertThat(startupLogs).contains("Configuration property 'application.kong.enabled' is deprecated and will be "
      + "removed in the Vetch release. Use 'application.apigw.enabled' instead.");
  }

  @Test
  void apiGatewayIntegration_positive_newVariablesTakePrecedenceOverLegacyOnes() {
    assertThat(appContext.containsBean("folioKongAdminClient")).isTrue();
    assertThat(appContext.containsBean("folioKongGatewayService")).isTrue();
    assertThat(apiGatewayProperties.getReadTimeout()).isEqualTo(222);
  }

  static class StartupLogCapturer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
      startupLogs = captureLog4J2Logs();
    }
  }
}
