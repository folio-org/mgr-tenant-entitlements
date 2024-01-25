package org.folio.entitlement.utils;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.utils.RoutingEntryUtils.getMethods;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;
import org.folio.common.domain.model.RoutingEntry;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class RoutingEntryUtilsTest {

  @MethodSource("getMethodsDataProvider")
  @DisplayName("getMethods_positive_parameterized")
  @ParameterizedTest(name = "[{index}] givenMethods={0}, expectedMethods={1}")
  void getMethods_positive_parameterized(List<String> methods, List<String> expected) {
    var given = new RoutingEntry().methods(methods);
    var result = getMethods(given);
    assertThat(result).containsExactlyElementsOf(expected);
  }

  public static Stream<Arguments> getMethodsDataProvider() {
    var allHttpMethods = List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE");
    return Stream.of(
      arguments(null, emptyList()),
      arguments(emptyList(), emptyList()),
      arguments(List.of("GET"), List.of("GET")),
      arguments(List.of("GET", "POST"), List.of("GET", "POST")),
      arguments(List.of("*"), allHttpMethods),
      arguments(List.of("POST", "*"), allHttpMethods)
    );
  }
}
