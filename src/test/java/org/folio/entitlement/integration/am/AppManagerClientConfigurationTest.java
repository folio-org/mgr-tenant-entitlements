package org.folio.entitlement.integration.am;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Contract;
import feign.codec.Decoder;
import feign.codec.Encoder;
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
  @Mock private Contract contract;
  @Mock private Encoder encoder;
  @Mock private Decoder decoder;

  @BeforeEach
  void setUp() {
    appManagerClientConfiguration.setUrl("https://test-app-manager.dev");
  }

  @Test
  void name() {
    var feignClient = appManagerClientConfiguration.applicationManagerClient(contract, encoder, decoder);
    assertThat(feignClient).isNotNull();
  }
}
