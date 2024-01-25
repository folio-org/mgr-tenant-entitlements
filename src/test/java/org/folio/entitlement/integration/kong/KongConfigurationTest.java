package org.folio.entitlement.integration.kong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.folio.test.types.UnitTest;
import org.folio.tools.kong.service.KongGatewayService;
import org.junit.jupiter.api.Test;

@UnitTest
class KongConfigurationTest {

  private final KongConfiguration kongConfiguration = new KongConfiguration();

  @Test
  void kongRouteCreator_positive() {
    var kongRouteCreator = kongConfiguration.kongRouteCreator(mock(KongGatewayService.class));
    assertThat(kongRouteCreator).isNotNull();
  }

  @Test
  void kongRouteCleaner_positive() {
    var kongRouteCleaner = kongConfiguration.kongRouteCleaner(mock(KongGatewayService.class));
    assertThat(kongRouteCleaner).isNotNull();
  }
}
