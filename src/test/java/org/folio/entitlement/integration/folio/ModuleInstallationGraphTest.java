package org.folio.entitlement.integration.folio;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.service.ModuleInstallationGraph;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class ModuleInstallationGraphTest {

  @MethodSource("applicationDescriptorDataProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  @DisplayName("getInstallationSequence_parameterized_applicationDescriptor")
  void getInstallationSequence_parameterized_applicationDescriptor(@SuppressWarnings("unused") String testName,
    ApplicationDescriptor descriptor, List<List<String>> expected) {
    var moduleInstallationGraph = new ModuleInstallationGraph(descriptor, ENTITLE);
    var result = moduleInstallationGraph.getInstallationSequence();
    assertThat(result).isEqualTo(expected);
  }

  @MethodSource("moduleDescriptorDataProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  @DisplayName("getInstallationSequence_parameterized_modules")
  void getInstallationSequence_parameterized_modules(@SuppressWarnings("unused") String testName,
    EntitlementType entitlementType, List<ModuleDescriptor> descriptor, List<List<String>> expected) {
    var moduleInstallationGraph = new ModuleInstallationGraph(descriptor, entitlementType);
    var result = moduleInstallationGraph.getInstallationSequence();
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void getInstallationSequence_revokeRequest() {
    var applicationDescriptor = appDescriptor(
      module("m1", List.of("m1-int")), module("m2", List.of("m2-int"), List.of("m1-int")));
    var moduleInstallationGraph = new ModuleInstallationGraph(applicationDescriptor, REVOKE);
    var result = moduleInstallationGraph.getInstallationSequence();
    assertThat(result).isEqualTo(List.of(List.of("m2"), List.of("m1")));
  }

  private static Stream<Arguments> applicationDescriptorDataProvider() {
    return Stream.of(
      arguments("empty modules", new ApplicationDescriptor(), emptyList()),

      arguments("Independent modules (count=1)",
        appDescriptor(module("m1", List.of("m1-i1", "m1-i2"))),
        List.of(List.of("m1"))),

      arguments("Independent modules (count=3)",
        appDescriptor(
          module("m1", List.of("m1-int1", "m1-int2")),
          module("m2", List.of("m2-int")),
          module("m3", List.of("m3-int", "m3-int2", "m3-int3"))),
        List.of(List.of("m1", "m2", "m3"))),

      arguments("Dependent modules (m1 <- m2)",
        appDescriptor(
          module("m1", List.of("m1-int")),
          module("m2", List.of("m2-int"), List.of("m1-int"))),
        List.of(List.of("m1"), List.of("m2"))),

      arguments("Dependent modules (m1 <- ui-m1)",
        new ApplicationDescriptor()
          .moduleDescriptors(List.of(module("m1", List.of("m1-int"))))
          .uiModuleDescriptors(List.of(module("ui-m1", emptyList(), List.of("m1-int")))),
        List.of(List.of("m1"), List.of("ui-m1"))),

      arguments("Dependent modules (m2 <- m1 <- m3)",
        appDescriptor(
          module("m1", List.of("m1-int"), List.of("m2-int")),
          module("m2", List.of("m2-int")),
          module("m3", List.of("m3-int"), List.of("m1-int"))),
        List.of(List.of("m2"), List.of("m1"), List.of("m3")))
    );
  }

  private static Stream<Arguments> moduleDescriptorDataProvider() {
    return Stream.of(
      arguments("null modules", ENTITLE, null, emptyList()),
      arguments("empty modules", ENTITLE, emptyList(), emptyList()),
      arguments("Dependent modules[null provides]", ENTITLE, List.of(module("m1", null)), List.of(List.of("m1"))),

      arguments("Independent modules (count=1)", ENTITLE,
        List.of(module("m1", List.of("m1-i1", "m1-i2"))),
        List.of(List.of("m1"))),

      arguments("Independent modules (count=3)", ENTITLE,
        List.of(
          module("m1", List.of("m1-int1", "m1-int2")),
          module("m2", List.of("m2-int")),
          module("m3", List.of("m3-int", "m3-int2", "m3-int3"))),
        List.of(List.of("m1", "m2", "m3"))),

      arguments("Independent modules [unknown interface]", ENTITLE,
        List.of(
          module("m1", List.of("m1-int")),
          module("m2", List.of("m2-int"), List.of("unknown"))),
        List.of(List.of("m1", "m2"))),

      arguments("Dependent modules (m1 <- m2)", ENTITLE,
        List.of(
          module("m1", List.of("m1-int")),
          module("m2", List.of("m2-int"), List.of("m1-int"))),
        List.of(List.of("m1"), List.of("m2"))),

      arguments("Dependent modules (m2 <- m1 <- m3)", ENTITLE,
        List.of(
          module("m1", List.of("m1-int"), List.of("m2-int")),
          module("m2", List.of("m2-int")),
          module("m3", List.of("m3-int"), List.of("m1-int"))),
        List.of(List.of("m2"), List.of("m1"), List.of("m3"))),

      arguments("Dependent modules (m2 <- (m1, m3) <- m4)", ENTITLE,
        List.of(
          module("m1", List.of("m1-i1"), List.of("m2-int")),
          module("m2", List.of("m2-int")),
          module("m3", List.of("m3-int"), List.of("m2-int")),
          module("m4", List.of("m4-int"), List.of("m3-int"))),
        List.of(List.of("m2"), List.of("m1", "m3"), List.of("m4"))),

      arguments("Dependent modules (m2 <- (m1, m3) <- m4)", REVOKE,
        List.of(
          module("m1", List.of("m1-i1"), List.of("m2-int")),
          module("m2", List.of("m2-int")),
          module("m3", List.of("m3-int"), List.of("m2-int")),
          module("m4", List.of("m4-int"), List.of("m3-int"))),
        List.of(List.of("m4"), List.of("m1", "m3"), List.of("m2"))),

      arguments("Dependent modules (m2 <- (m1, m3) <- m4(depends on m3 and m2))", ENTITLE,
        List.of(
          module("m1", List.of("m1-i1"), List.of("m2-int")),
          module("m2", List.of("m2-int")),
          module("m3", List.of("m3-int"), List.of("m2-int")),
          module("m4", List.of("m4-int"), List.of("m2-int", "m3-int"))),
        List.of(List.of("m2"), List.of("m1", "m3"), List.of("m4"))),

      arguments("Dependent modules (m1 <- m4, m2 <- m3)", ENTITLE,
        List.of(
          module("m1", List.of("m1-int")),
          module("m2", List.of("m2-int")),
          module("m3", List.of("m3-int"), List.of("m2-int")),
          module("m4", List.of("m4-int"), List.of("m1-int"))),
        List.of(List.of("m1", "m2"), List.of("m3", "m4"))),

      arguments("Dependent modules ((m1, m2) <- m3)", ENTITLE,
        List.of(
          module("m1", List.of("m1-int")),
          module("m2", List.of("m2-int")),
          module("m3", List.of("m3-int"), List.of("m1-int", "m2-int"))),
        List.of(List.of("m1", "m2"), List.of("m3"))),

      arguments("Dependent modules[self-reference] (m1 <- m2 <- m2)", ENTITLE,
        List.of(
          module("m1", List.of("m1-int")),
          module("m2", List.of("m2-int"), List.of("m2-int"))),
        List.of(List.of("m1", "m2"))),

      arguments("Circular dependency [m1 <-> m2]", ENTITLE,
        List.of(
          module("m1", List.of("m1-int"), List.of("m2-int")),
          module("m2", List.of("m2-int"), List.of("m1-int"))),
        List.of(List.of("m1", "m2"))),

      arguments("Circular dependency [3 modules]", ENTITLE,
        List.of(
          module("m1", List.of("m1-int"), List.of("m2-int")),
          module("m2", List.of("m2-int"), List.of("m3-int")),
          module("m3", List.of("m3-int"), List.of("m1-int"))),
        List.of(List.of("m1", "m2", "m3"))),

      arguments("Circular dependency [m1 <- m2 <-> m3]", ENTITLE,
        List.of(
          module("m1", List.of("m1-int")),
          module("m2", List.of("m2-int"), List.of("m1-int", "m3-int")),
          module("m3", List.of("m3-int"), List.of("m2-int"))),
        List.of(List.of("m1"), List.of("m2", "m3"))),

      arguments("Circular dependency [m1 <- m2 <-> m3 <- m4]", ENTITLE,
        List.of(
          module("m1", List.of("m1-api")),
          module("m2", List.of("m2-api"), List.of("m1-api", "m3-api")),
          module("m3", List.of("m3-api"), List.of("m2-api")),
          module("m4", List.of("m4-api"), List.of("m3-api"))),
        List.of(List.of("m1"), List.of("m2", "m3"), List.of("m4"))),

      arguments("Circular dependency [m1 <- (m2, m3), m4 <- m5]", ENTITLE,
        List.of(
          module("m1", List.of("m1-api")),
          module("m2", List.of("m2-api"), List.of("m1-api", "m3-api")),
          module("m3", List.of("m3-api"), List.of("m2-api")),
          module("m4", List.of("m4-api"), List.of("m1-api")),
          module("m5", List.of("m5-api"), List.of("m3-api"))),
        List.of(List.of("m1"), List.of("m2", "m3", "m4"), List.of("m5"))),

      arguments("Two circular dependencies [m1 <- (m2 <-> m3, m4 <-> m5) <- m6]", ENTITLE,
        List.of(
          module("m1", List.of("m1-api")),
          module("m2", List.of("m2-api"), List.of("m1-api", "m3-api")),
          module("m3", List.of("m3-api"), List.of("m2-api")),
          module("m4", List.of("m4-api"), List.of("m1-api", "m5-api")),
          module("m5", List.of("m5-api"), List.of("m4-api")),
          module("m6", List.of("m6-api"), List.of("m5-api"))),
        List.of(List.of("m1"), List.of("m2", "m3", "m4", "m5"), List.of("m6"))),

      arguments("Two circular dependencies [m1 <- m2 <-> m3 <- m4 <-> m5 <- m6]", ENTITLE,
        List.of(
          module("m1", List.of("m1-api")),
          module("m2", List.of("m2-api"), List.of("m1-api", "m3-api")),
          module("m3", List.of("m3-api"), List.of("m2-api")),
          module("m4", List.of("m4-api"), List.of("m3-api", "m5-api")),
          module("m5", List.of("m5-api"), List.of("m4-api")),
          module("m6", List.of("m6-api"), List.of("m5-api"))),
        List.of(List.of("m1"), List.of("m2", "m3"), List.of("m4", "m5"), List.of("m6"))),

      arguments("Two circular dependencies [m1 <- (m2, m3, m4) <-> (m2, m5, m6) <- m7]", ENTITLE,
        List.of(
          module("m1", List.of("m1-api")),
          module("m2", List.of("m2-api"), List.of("m1-api", "m3-api", "m5-api")),
          module("m3", List.of("m3-api"), List.of("m4-api")),
          module("m4", List.of("m4-api"), List.of("m2-api")),
          module("m5", List.of("m5-api"), List.of("m6-api")),
          module("m6", List.of("m6-api"), List.of("m2-api")),
          module("m7", List.of("m7-api"), List.of("m2-api"))),
        List.of(List.of("m1"), List.of("m2", "m3", "m4", "m5", "m6"), List.of("m7"))),

      arguments("Two circular dependencies [m1 <- (m2, m3, m4) <- (m2, m5) -> (m5, m6, m7) <- m8]", ENTITLE,
        List.of(
          module("m1", List.of("m1-api")),
          module("m2", List.of("m2-api"), List.of("m1-api", "m3-api", "m5-api")),
          module("m3", List.of("m3-api"), List.of("m4-api", "m6-api")),
          module("m4", List.of("m4-api"), List.of("m2-api")),
          module("m5", List.of("m5-api"), List.of("m6-api", "m2-api")),
          module("m6", List.of("m6-api"), List.of("m7-api")),
          module("m7", List.of("m7-api"), List.of("m5-api")),
          module("m8", List.of("m8-api"), List.of("m5-api"))),
        List.of(List.of("m1"), List.of("m2", "m3", "m4", "m5", "m6", "m7"), List.of("m8")))
    );
  }

  private static ApplicationDescriptor appDescriptor(ModuleDescriptor... moduleDescriptors) {
    return new ApplicationDescriptor().id(APPLICATION_ID).moduleDescriptors(List.of(moduleDescriptors));
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
