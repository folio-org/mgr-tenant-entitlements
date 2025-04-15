package org.folio.entitlement.service;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.support.TestValues.appDescriptor;
import static org.folio.entitlement.support.TestValues.dependency;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class ApplicationInstallationGraphTest {

  @MethodSource("getInstallationSequenceDataProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  void getInstallationSequence_positive(@SuppressWarnings("unused") String testName,
    List<ApplicationDescriptor> descriptors, List<Set<String>> expected) {
    var graph = new ApplicationInstallationGraph(descriptors);
    var result = graph.getInstallationSequence();

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void getInstallationSequence_negative_circDependencies() {
    List<ApplicationDescriptor> descriptors = List.of(
      appDescriptor("app-foo-1.0.0", dependency("app-baz-1.0.0")),
      appDescriptor("app-bar-1.0.0", dependency("app-foo-1.0.0")),
      appDescriptor("app-baz-1.0.0", dependency("app-bar-1.0.0")));

    var graph = new ApplicationInstallationGraph(descriptors);
    assertThatThrownBy(graph::getInstallationSequence)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No more independent applications can be found");
  }

  private static Stream<Arguments> getInstallationSequenceDataProvider() {
    return Stream.of(
      arguments("No applications", emptyList(), emptyList()),

      arguments("Single application (no deps)", List.of(
        appDescriptor("app-foo-1.0.0")), List.of(Set.of("app-foo-1.0.0"))),

      arguments("Single application (with dep)",
        List.of(appDescriptor("app-foo-1.0.0", dependency("app-bar-1.0.0"))),
        List.of(Set.of("app-foo-1.0.0"))),

      arguments("Multiple applications (not connected)",
        List.of(appDescriptor("app-foo-1.0.0", dependency("app-x-1.0.0")),
          appDescriptor("app-bar-1.0.0", dependency("app-y-1.0.0")),
          appDescriptor("app-baz-1.0.0", dependency("app-z-1.0.0"))),
        List.of(Set.of("app-foo-1.0.0", "app-bar-1.0.0", "app-baz-1.0.0"))),

      arguments("Multiple applications (app-foo <- app-bar, app-baz)",
        List.of(appDescriptor("app-foo-1.0.0", dependency("app-x-1.0.0")),
          appDescriptor("app-bar-1.0.0", dependency("app-foo-1.0.0")),
          appDescriptor("app-baz-1.0.0", dependency("app-z-1.0.0"))),
        List.of(Set.of("app-foo-1.0.0", "app-baz-1.0.0"),
          Set.of("app-bar-1.0.0"))),

      arguments("Multiple applications ({app-foo, app-bar} <- app-baz)",
        List.of(appDescriptor("app-foo-1.0.0", dependency("app-x-1.0.0")),
          appDescriptor("app-bar-1.0.0", dependency("app-y-1.0.0")),
          appDescriptor("app-baz-1.0.0", dependency("app-foo-1.0.0"), dependency("app-bar-1.0.0"))),
        List.of(Set.of("app-foo-1.0.0", "app-bar-1.0.0"),
          Set.of("app-baz-1.0.0"))),

      arguments("Multiple applications (app-foo <- app-bar <- app-baz)",
        List.of(appDescriptor("app-foo-1.0.0", dependency("app-x-1.0.0")),
          appDescriptor("app-bar-1.0.0", dependency("app-foo-1.0.0")),
          appDescriptor("app-baz-1.0.0", dependency("app-bar-1.0.0"))),
        List.of(Set.of("app-foo-1.0.0"),
          Set.of("app-bar-1.0.0"),
          Set.of("app-baz-1.0.0"))),

      arguments("Multiple applications (app-foo <- app-bar, app-baz <- app-qux)",
        List.of(appDescriptor("app-foo-1.0.0", dependency("app-x-1.0.0")),
          appDescriptor("app-bar-1.0.0", dependency("app-foo-1.0.0")),
          appDescriptor("app-baz-1.0.0", dependency("app-y-1.0.0")),
          appDescriptor("app-qux-1.0.0", dependency("app-baz-1.0.0"))),
        List.of(Set.of("app-foo-1.0.0", "app-baz-1.0.0"),
          Set.of("app-bar-1.0.0", "app-qux-1.0.0"))),

      arguments("Multiple applications (app-foo <- app-bar, {app-foo, app-baz} <- app-qux <- app-corge)",
        List.of(appDescriptor("app-foo-1.0.0", dependency("app-x-1.0.0")),
          appDescriptor("app-bar-1.0.0", dependency("app-foo-1.0.0")),
          appDescriptor("app-baz-1.0.0", dependency("app-y-1.0.0")),
          appDescriptor("app-qux-1.0.0", dependency("app-foo-1.0.0"), dependency("app-baz-1.0.0")),
          appDescriptor("app-corge-1.0.0", dependency("app-qux-1.0.0"))),
        List.of(Set.of("app-foo-1.0.0", "app-baz-1.0.0"),
          Set.of("app-bar-1.0.0", "app-qux-1.0.0"),
          Set.of("app-corge-1.0.0")))
    );
  }
}
