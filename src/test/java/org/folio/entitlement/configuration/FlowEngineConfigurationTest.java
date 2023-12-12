package org.folio.entitlement.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FlowEngineConfigurationTest {

  @InjectMocks private FlowEngineConfiguration flowEngineConfiguration;
  @Mock private FlowEngineConfigurationProperties configurationProperties;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(configurationProperties);
  }

  @Test
  void flowEngine_positive() {
    when(configurationProperties.getPrintFlowResult()).thenReturn(false);
    when(configurationProperties.getExecutionTimeout()).thenReturn(Duration.ofMillis(100));
    when(configurationProperties.getLastExecutionsStatusCacheSize()).thenReturn(20);

    var flowEngine = flowEngineConfiguration.flowEngine(configurationProperties);

    assertThat(flowEngine).isNotNull();
  }
}
