package org.folio.entitlement.integration.apigw;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.folio.test.types.UnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class ApiGatewayConfigurationTest {

  @ParameterizedTest
  @ValueSource(strings = {"kong", "apisix"})
  void validateGatewayType_positive(String type) {
    assertThatNoException().isThrownBy(() -> ApiGatewayConfiguration.validateGatewayType(type));
  }

  @ParameterizedTest
  @ValueSource(strings = {"apisx", "", "nginx"})
  void validateGatewayType_negative_unsupportedType(String type) {
    assertThatThrownBy(() -> ApiGatewayConfiguration.validateGatewayType(type))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Unsupported API Gateway type")
      .hasMessageContaining("APIGW_TYPE");
  }
}
