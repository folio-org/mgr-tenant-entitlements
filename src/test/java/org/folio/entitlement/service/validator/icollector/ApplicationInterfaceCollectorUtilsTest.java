package org.folio.entitlement.service.validator.icollector;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.common.utils.SemverUtils.getName;
import static org.folio.entitlement.service.validator.icollector.ApplicationInterfaceCollectorUtils.populateProvidedFromApp;
import static org.folio.entitlement.service.validator.icollector.ApplicationInterfaceCollectorUtils.populateRequiredAndProvidedFromApp;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.InterfaceItem;
import org.folio.entitlement.service.validator.icollector.ApplicationInterfaceCollector.RequiredProvidedInterfaces;
import org.folio.entitlement.support.TestValues;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ApplicationInterfaceCollectorUtilsTest {

  private static final String APP_FOO = "app-foo-1.0.0";

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("populateRequiredAndProvidedFromAppDataProvider")
  void populateRequiredAndProvidedFromApp_positive(@SuppressWarnings("unused") String testName,
    ApplicationDescriptor application, RequiredProvidedInterfaces expected) {
    var actual = populateRequiredAndProvidedFromApp(application);
    assertThat(actual).isEqualTo(expected);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("populateProvidedFromAppDataProvider")
  void populateProvidedFromApp_positive(@SuppressWarnings("unused") String testName,
    ApplicationDescriptor application, RequiredProvidedInterfaces expected) {
    var actual = populateProvidedFromApp(application);
    assertThat(actual).isEqualTo(expected);
  }

  private static Stream<Arguments> populateRequiredAndProvidedFromAppDataProvider() {
    return Stream.of(
      arguments("Application with no modules",
        appDescriptor(emptyList(), emptyList()),
        new RequiredProvidedInterfaces(emptySet(), emptyMap())
      ),

      arguments("Application with back-end modules",
        appDescriptor(
          // modules
          List.of(
            modDescriptor("mod-foo-1.0.0",
              // provided
              List.of(itfDescriptor("foo-a", "1.0"), itfDescriptor("foo-b", "1.0"), itfDescriptor("foo-c", "1.0")),
              // required
              List.of(itfRef("bar-a", "1.0"), itfRef("bar-b", "1.0"))
            ),
            modDescriptor("mod-bar-1.0.0",
              // provided
              List.of(itfDescriptor("bar-a", "1.0"), itfDescriptor("bar-b", "1.0")),
              // required
              emptyList()
            )
          ),
          // ui modules
          emptyList()
        ),
        new RequiredProvidedInterfaces(
          // required
          Set.of(itfItem("bar-a", "1.0"), itfItem("bar-b", "1.0")),
          // provided
          Map.of(
            "foo-a", Set.of(itfItem("foo-a", "1.0")),
            "foo-b", Set.of(itfItem("foo-b", "1.0")),
            "foo-c", Set.of(itfItem("foo-c", "1.0")),
            "bar-a", Set.of(itfItem("bar-a", "1.0")),
            "bar-b", Set.of(itfItem("bar-b", "1.0"))
          )
        )
      ),

      arguments("Application with back-end/ui modules",
        appDescriptor(
          // modules
          List.of(
            modDescriptor("mod-foo-1.0.0",
              // provided
              List.of(itfDescriptor("foo-a", "1.0"), itfDescriptor("foo-b", "1.0"), itfDescriptor("foo-c", "1.0")),
              // required
              List.of(itfRef("bar-a", "1.0"), itfRef("bar-b", "1.0"))
            )
          ),
          // ui modules
          List.of(
            modDescriptor("ui-foo-1.0.0",
              // provided
              emptyList(),
              // required
              List.of(itfRef("foo-a", "1.0"), itfRef("foo-b", "1.0"), itfRef("foo-c", "1.0"))
            ),
            modDescriptor("ui-bar-1.0.0",
              // provided
              emptyList(),
              // required
              List.of(itfRef("bar-a", "1.0"), itfRef("bar-b", "1.0"))
            )
          )
        ),
        new RequiredProvidedInterfaces(
          // required
          Set.of(itfItem("bar-a", "1.0"), itfItem("bar-b", "1.0"),
            itfItem("foo-a", "1.0"), itfItem("foo-b", "1.0"), itfItem("foo-c", "1.0")),
          // provided
          Map.of(
            "foo-a", Set.of(itfItem("foo-a", "1.0")),
            "foo-b", Set.of(itfItem("foo-b", "1.0")),
            "foo-c", Set.of(itfItem("foo-c", "1.0")))
          )
      ),

      arguments("Application with ui modules",
        appDescriptor(
          // modules
          emptyList(),
          // ui modules
          List.of(
            modDescriptor("ui-foo-1.0.0",
              // provided
              emptyList(),
              // required
              List.of(itfRef("foo-a", "1.0"), itfRef("foo-b", "1.0"), itfRef("foo-c", "1.0"))
            ),
            modDescriptor("ui-bar-1.0.0",
              // provided
              emptyList(),
              // required
              List.of(itfRef("bar-a", "1.0"), itfRef("bar-b", "1.0"))
            )
          )
        ),
        new RequiredProvidedInterfaces(
          // required
          Set.of(itfItem("bar-a", "1.0"), itfItem("bar-b", "1.0"),
            itfItem("foo-a", "1.0"), itfItem("foo-b", "1.0"), itfItem("foo-c", "1.0")),
          // provided
          emptyMap()
        )
      ),

      arguments("Application with system interfaces",
        appDescriptor(
          // modules
          List.of(
            modDescriptor("mod-foo-1.0.0",
              // provided
              List.of(itfDescriptor("foo-a", "1.0"), itfDescriptor("foo-b", "1.0"),
                itfDescriptor("foo-system", "1.0", "system")),
              // required
              List.of(itfRef("bar-a", "1.0"), itfRef("bar-b", "1.0"))
            ),
            modDescriptor("mod-bar-1.0.0",
              // provided
              List.of(itfDescriptor("bar-a", "1.0"), itfDescriptor("bar-b", "1.0"),
                itfDescriptor("bar-system", "1.0", "system")),
              // required
              List.of(itfRef("foo-a", "1.0"), itfRef("foo-b", "1.0"))
            )
          ),
          // ui modules
          emptyList()
        ),
        new RequiredProvidedInterfaces(
          // required
          Set.of(itfItem("bar-a", "1.0"), itfItem("bar-b", "1.0"),
            itfItem("foo-a", "1.0"), itfItem("foo-b", "1.0")),
          // provided
          Map.of(
            "foo-a", Set.of(itfItem("foo-a", "1.0")),
            "foo-b", Set.of(itfItem("foo-b", "1.0")),
            "bar-a", Set.of(itfItem("bar-a", "1.0")),
            "bar-b", Set.of(itfItem("bar-b", "1.0")))
        )
      )
    );
  }

  private static Stream<Arguments> populateProvidedFromAppDataProvider() {
    return populateRequiredAndProvidedFromAppDataProvider().map(arguments -> {
      var reqProv = (RequiredProvidedInterfaces) arguments.get()[2];
      return arguments(
        arguments.get()[0],
        arguments.get()[1],
        new RequiredProvidedInterfaces(emptySet(), reqProv.provided())
      );
    });
  }

  private static ApplicationDescriptor appDescriptor(List<ModuleDescriptor> modules,
    List<ModuleDescriptor> uiModules) {
    return TestValues.appDescriptor(APP_FOO)
      .moduleDescriptors(modules)
      .uiModuleDescriptors(uiModules);
  }

  private static ModuleDescriptor modDescriptor(String id, List<InterfaceDescriptor> providedInterfaces,
    List<InterfaceReference> requiredInterfaces) {
    return new ModuleDescriptor().id(id).description(getName(id))
      .requires(requiredInterfaces)
      .provides(providedInterfaces);
  }

  private static InterfaceDescriptor itfDescriptor(String id, String version, String type) {
    return new InterfaceDescriptor().id(id)
      .version(version)
      .interfaceType(type);
  }

  private static InterfaceDescriptor itfDescriptor(String id, String version) {
    return new InterfaceDescriptor().id(id)
      .version(version);
  }

  private static InterfaceReference itfRef(String id, String version) {
    return new InterfaceReference().id(id).version(version);
  }

  private static InterfaceItem itfItem(String id, String version) {
    return new InterfaceItem(itfRef(id, version), APP_FOO);
  }
}
