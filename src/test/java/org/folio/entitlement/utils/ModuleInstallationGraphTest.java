package org.folio.entitlement.utils;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.folio.ModuleInstallationGraph;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class ModuleInstallationGraphTest {

  @MethodSource("applicationDescriptorDataProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  @DisplayName("getModuleInstallationSequence_parameterized")
  void getModuleInstallationSequence_parameterized(@SuppressWarnings("unused") String testName,
    ApplicationDescriptor descriptor, List<Set<String>> expected) {
    var moduleInstallationGraph = new ModuleInstallationGraph(descriptor);
    var result = moduleInstallationGraph.getModuleInstallationSequence();
    assertThat(result).isEqualTo(expected);
  }

  private static Stream<Arguments> applicationDescriptorDataProvider() {
    return Stream.of(
      arguments("empty modules", new ApplicationDescriptor(), emptyList()),

      arguments("Independent modules (count=1)",
        appDescriptor(module("m1", List.of("m1-i1", "m1-i2"))),
        List.of(Set.of("m1"))),

      arguments("Independent modules (count=3)",
        appDescriptor(
          module("m1", List.of("m1-int1", "m1-int2")),
          module("m2", List.of("m2-int")),
          module("m3", List.of("m3-int", "m3-int2", "m3-int3"))),
        List.of(Set.of("m1", "m2", "m3"))),

      arguments("Independent modules [unknown interface]",
        appDescriptor(
          module("m1", List.of("m1-int")),
          module("m2", List.of("m2-int"), List.of("unknown"))),
        List.of(Set.of("m1", "m2"))),

      arguments("Dependent modules (m1 <- m2)",
        appDescriptor(
          module("m1", List.of("m1-int")),
          module("m2", List.of("m2-int"), List.of("m1-int"))),
        List.of(Set.of("m1"), Set.of("m2"))),

      arguments("Dependent modules (m2 <- m1 <- m3)",
        appDescriptor(
          module("m1", List.of("m1-int"), List.of("m2-int")),
          module("m2", List.of("m2-int")),
          module("m3", List.of("m3-int"), List.of("m1-int"))),
        List.of(Set.of("m2"), Set.of("m1"), Set.of("m3"))),

      arguments("Dependent modules (m2 <- (m1, m3) <- m4)",
        appDescriptor(
          module("m1", List.of("m1-i1"), List.of("m2-int")),
          module("m2", List.of("m2-int")),
          module("m3", List.of("m3-int"), List.of("m2-int")),
          module("m4", List.of("m4-int"), List.of("m3-int"))),
        List.of(Set.of("m2"), Set.of("m1", "m3"), Set.of("m4"))),

      arguments("Dependent modules (m2 <- (m1, m3) <- m4(depends on m3 and m2))",
        appDescriptor(
          module("m1", List.of("m1-i1"), List.of("m2-int")),
          module("m2", List.of("m2-int")),
          module("m3", List.of("m3-int"), List.of("m2-int")),
          module("m4", List.of("m4-int"), List.of("m2-int", "m3-int"))),
        List.of(Set.of("m2"), Set.of("m1", "m3"), Set.of("m4"))),

      arguments("Dependent modules (m1 <- m4, m2 <- m3)",
        appDescriptor(
          module("m1", List.of("m1-int")),
          module("m2", List.of("m2-int")),
          module("m3", List.of("m3-int"), List.of("m2-int")),
          module("m4", List.of("m4-int"), List.of("m1-int"))),
        List.of(Set.of("m1", "m2"), Set.of("m3", "m4"))),

      arguments("Dependent modules ((m1, m2) <- m3)",
        appDescriptor(
          module("m1", List.of("m1-int")),
          module("m2", List.of("m2-int")),
          module("m3", List.of("m3-int"), List.of("m1-int", "m2-int"))),
        List.of(Set.of("m1", "m2"), Set.of("m3"))),

      arguments("Dependent modules[self-reference] (m1 <- m2 <- m2)",
        appDescriptor(
          module("m1", List.of("m1-int")),
          module("m2", List.of("m2-int"), List.of("m2-int"))),
        List.of(Set.of("m1", "m2"))),

      arguments("Dependent modules[null provides]", appDescriptor(module("m1", null)), List.of(Set.of("m1")))
    );
  }

  private static ApplicationDescriptor appDescriptor(ModuleDescriptor... moduleDescriptors) {
    return new ApplicationDescriptor().id("test").moduleDescriptors(List.of(moduleDescriptors));
  }

  private static ModuleDescriptor module(String id, List<String> provides) {
    return module(id, provides, emptyList());
  }

  private static ModuleDescriptor module(String id, List<String> provides, List<String> requires) {
    return new ModuleDescriptor()
      .id(id)
      .provides(mapItems(provides, name -> new InterfaceDescriptor(name, "1.0")))
      .requires(mapItems(requires, name -> InterfaceReference.of(name, "1.0")));
  }
}
