package org.folio.entitlement.service;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.common.utils.CollectionUtils.mapItemsToSet;
import static org.folio.entitlement.support.TestValues.appDescriptor;
import static org.folio.entitlement.support.TestValues.dependency;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.service.ScopedApplicationInterfaceCollector.ApplicationDependencyResolver;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

@UnitTest
class ApplicationDependencyResolverTest {

  @ParameterizedTest
  @NullAndEmptySource
  void constructor_negative_whenApplicationListIsEmpty(List<ApplicationDescriptor> allApps) {
    assertThatThrownBy(() -> new ApplicationDependencyResolver(allApps))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("No applications provided");
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("getAllDependenciesDataProvider")
  void getAllDependencies_positive(@SuppressWarnings("unused") String testName, ApplicationDescriptor targetApp,
    List<ApplicationDescriptor> allApps, Set<String> expected) {
    var resolver = new ApplicationDependencyResolver(allApps);

    var result = resolver.getAllDependencies(targetApp);
    var ids = mapItemsToSet(result, ApplicationDescriptor::getId);

    assertThat(ids).isEqualTo(expected);
  }

  @Test
  void getAllDependencies_positive_multipleRandomizedCalls() {
    var allApplications = List.of(
      appDescriptor("app-foo-1.0.0", dependency("app-bar-1.0.0"), dependency("app-baz-1.0.0")),
      appDescriptor("app-bar-1.0.0"),
      appDescriptor("app-baz-1.0.0", dependency("app-qux-1.0.0"), dependency("app-corge-1.0.0")),
      appDescriptor("app-qux-1.0.0", dependency("app-quux-1.0.0")),
      appDescriptor("app-corge-1.0.0"),
      appDescriptor("app-quux-1.0.0")
    );

    var expectedDependencies = Map.of(
      "app-foo-1.0.0", Set.of("app-bar-1.0.0", "app-baz-1.0.0", "app-qux-1.0.0", "app-corge-1.0.0", "app-quux-1.0.0"),
      "app-bar-1.0.0", emptySet(),
      "app-baz-1.0.0", Set.of("app-qux-1.0.0", "app-corge-1.0.0", "app-quux-1.0.0"),
      "app-qux-1.0.0", Set.of("app-quux-1.0.0"),
      "app-corge-1.0.0", emptySet(),
      "app-quux-1.0.0", emptySet());

    var resolver = new ApplicationDependencyResolver(allApplications);

    var shuffled = new ArrayList<>(allApplications);
    Collections.shuffle(shuffled);

    for (ApplicationDescriptor app : shuffled) {
      var result = resolver.getAllDependencies(app);
      var ids = mapItemsToSet(result, ApplicationDescriptor::getId);

      assertThat(ids).isEqualTo(expectedDependencies.get(app.getId()));
    }
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("getAllDependenciesNegativeCircularDependencyDataProvider")
  void getAllDependencies_negative_whenCircularDependency(@SuppressWarnings("unused") String testName,
    ApplicationDescriptor targetApp, List<ApplicationDescriptor> allApps, String expectedMsg) {
    var resolver = new ApplicationDependencyResolver(allApps);

    assertThatThrownBy(() -> resolver.getAllDependencies(targetApp))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Circular application dependency detected")
      .hasMessageContaining(expectedMsg);
  }

  private static Stream<Arguments> getAllDependenciesDataProvider() {
    return Stream.of(
      arguments("Application with no deps",
        appDescriptor("app-foo-1.0.0"), List.of(appDescriptor("app-foo-1.0.0")),
        emptySet()),

      arguments("Application with one direct dep",
        appDescriptor("app-foo-1.0.0", dependency("app-bar-1.0.0")),
        List.of(
          appDescriptor("app-foo-1.0.0", dependency("app-bar-1.0.0")),
          appDescriptor("app-bar-1.0.0")
        ),
        Set.of("app-bar-1.0.0")),

      arguments("Application with multiple direct dep",
        appDescriptor("app-foo-1.0.0",
          dependency("app-bar-1.0.0"), dependency("app-baz-1.0.0"), dependency("app-qux-1.0.0")),
        List.of(
          appDescriptor("app-foo-1.0.0",
            dependency("app-bar-1.0.0"), dependency("app-baz-1.0.0"), dependency("app-qux-1.0.0")),
          appDescriptor("app-bar-1.0.0"),
          appDescriptor("app-baz-1.0.0"),
          appDescriptor("app-qux-1.0.0")
        ),
        Set.of("app-bar-1.0.0", "app-baz-1.0.0", "app-qux-1.0.0")),

      arguments("Application with direct and transitive dep",
        appDescriptor("app-foo-1.0.0", dependency("app-bar-1.0.0"), dependency("app-baz-1.0.0")),
        List.of(
          appDescriptor("app-foo-1.0.0", dependency("app-bar-1.0.0"), dependency("app-baz-1.0.0")),
          appDescriptor("app-bar-1.0.0"),
          appDescriptor("app-baz-1.0.0", dependency("app-qux-1.0.0"), dependency("app-corge-1.0.0")),
          appDescriptor("app-qux-1.0.0", dependency("app-quux-1.0.0")),
          appDescriptor("app-corge-1.0.0"),
          appDescriptor("app-quux-1.0.0")
        ),
        Set.of("app-bar-1.0.0", "app-baz-1.0.0", "app-qux-1.0.0", "app-corge-1.0.0", "app-quux-1.0.0"))
    );
  }

  private static Stream<Arguments> getAllDependenciesNegativeCircularDependencyDataProvider() {
    return Stream.of(
      arguments("Circular dependency to top-level app",
        appDescriptor("app-foo-1.0.0", dependency("app-bar-1.0.0"), dependency("app-baz-1.0.0")),
        List.of(
          appDescriptor("app-foo-1.0.0", dependency("app-bar-1.0.0"), dependency("app-baz-1.0.0")),
          appDescriptor("app-bar-1.0.0"),
          appDescriptor("app-baz-1.0.0", dependency("app-qux-1.0.0"), dependency("app-corge-1.0.0")),
          appDescriptor("app-qux-1.0.0", dependency("app-quux-1.0.0")),
          appDescriptor("app-corge-1.0.0"),
          appDescriptor("app-quux-1.0.0", dependency("app-foo-1.0.0"))
        ),
        "Chain is: app-foo <- app-quux <- app-qux <- app-baz <- app-foo"),

      arguments("Circular dependency inside deps",
        appDescriptor("app-foo-1.0.0", dependency("app-bar-1.0.0"), dependency("app-baz-1.0.0")),
        List.of(
          appDescriptor("app-foo-1.0.0", dependency("app-bar-1.0.0"), dependency("app-baz-1.0.0")),
          appDescriptor("app-bar-1.0.0"),
          appDescriptor("app-baz-1.0.0", dependency("app-qux-1.0.0"), dependency("app-corge-1.0.0")),
          appDescriptor("app-qux-1.0.0", dependency("app-quux-1.0.0")),
          appDescriptor("app-corge-1.0.0"),
          appDescriptor("app-quux-1.0.0", dependency("app-baz-1.0.0"))
        ),
        "Chain is: app-baz <- app-quux <- app-qux <- app-baz <- app-foo")
    );
  }
}
