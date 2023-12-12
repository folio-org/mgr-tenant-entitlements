package org.folio.entitlement.integration.folio;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;
import org.folio.common.domain.model.error.Parameter;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class FolioIntegrationUtilsTest {

  @DisplayName("parseTenantParameters_parameterized")
  @MethodSource("tenantParametersStringDataProvider")
  @ParameterizedTest(name = "[{index}] tenantParameterString={0}")
  void parseTenantParameters_parameterized(String tenantParametersString, List<Parameter> expected) {
    var result = FolioIntegrationUtils.parseTenantParameters(tenantParametersString);
    assertThat(result).isEqualTo(expected);
  }

  private static Stream<Arguments> tenantParametersStringDataProvider() {
    return Stream.of(
      arguments(null, emptyList()),
      arguments("", emptyList()),
      arguments("   ", emptyList()),
      arguments("loadReference=true", List.of(parameter("loadReference", "true"))),
      arguments("loadReference", List.of(parameter("loadReference", null))),
      arguments("loadReference=true,loadSamples=all", List.of(
        parameter("loadReference", "true"),
        parameter("loadSamples", "all")))
    );
  }

  private static Parameter parameter(String key, String value) {
    return new Parameter().key(key).value(value);
  }
}
