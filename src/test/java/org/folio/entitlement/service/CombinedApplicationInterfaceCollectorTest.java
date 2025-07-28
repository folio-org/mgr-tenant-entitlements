package org.folio.entitlement.service;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.readApplicationDescriptor;
import static org.folio.entitlement.support.TestValues.itfItem;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.service.validator.ApplicationInterfaceCollector.RequiredProvidedInterfaces;
import org.folio.entitlement.service.validator.configuration.ApplicationInterfaceCollectorProperties;
import org.folio.entitlement.service.validator.configuration.CollectedInterfaceSettings;
import org.folio.entitlement.service.validator.CombinedApplicationInterfaceCollector;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CombinedApplicationInterfaceCollectorTest {

  private static final Entitlement ENTITLEMENT_APP1 =
    new Entitlement().applicationId("folio-app1-1.0.0").tenantId(TENANT_ID);
  private static final Entitlement ENTITLEMENT_APP2 =
    new Entitlement().applicationId("folio-app2-1.0.0").tenantId(TENANT_ID);
  private static final Entitlement ENTITLEMENT_APP3 =
    new Entitlement().applicationId("folio-app3-1.0.0").tenantId(TENANT_ID);

  private static final ApplicationDescriptor APP_DESCRIPTOR1 =
    readApplicationDescriptor("json/ic/folio-app1-full.json");
  private static final ApplicationDescriptor APP_DESCRIPTOR2 =
    readApplicationDescriptor("json/ic/folio-app2-full.json");
  private static final ApplicationDescriptor APP_DESCRIPTOR3 =
    readApplicationDescriptor("json/ic/folio-app3-full.json");

  private static final List<RequiredProvidedInterfaces> REQUIRED_PROVIDED_INTERFACES = List.of(
    new RequiredProvidedInterfaces(
      Set.of(
        itfItem("folio-module3-api", "1.0", "folio-app3-1.0.0"),
        itfItem("folio-module1-api", "1.0", "folio-app2-1.0.0"),
        itfItem("folio-module1-api", "1.0", "folio-app1-1.0.0"),
        itfItem("folio-module2-api", "1.0", "folio-app3-1.0.0")
      ),
      Map.of(
        "folio-module3-api",
        Set.of(itfItem("folio-module3-api", "1.0", "folio-app3-1.0.0")),
        "folio-module2-api",
        Set.of(itfItem("folio-module2-api", "1.0", "folio-app2-1.0.0")),
        "folio-module1-api",
        Set.of(itfItem("folio-module1-api", "1.0", "folio-app1-1.0.0"))
      )
    )
  );

  @Mock private EntitlementCrudService entitlementCrudService;

  @Nested
  @DisplayName("CombinedApplicationInterfaceCollector::RequiredInterfacesIncluded")
  class IncludeRequiredInterfacesOfInstalledAppsTest {

    private CombinedApplicationInterfaceCollector collector;

    @BeforeEach
    void setUp() {
      var required = new CollectedInterfaceSettings();
      required.setExcludeEntitled(false);
      var properties = new ApplicationInterfaceCollectorProperties();
      properties.setRequired(required);

      this.collector = new CombinedApplicationInterfaceCollector(entitlementCrudService, properties);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("requiredInterfacesIncludedDataProvider")
    void collectRequiredAndProvided_positive(@SuppressWarnings("unused") String testName,
      List<ApplicationDescriptor> descriptors, List<Entitlement> entitlements,
      List<RequiredProvidedInterfaces> expected) {
      when(entitlementCrudService.findByApplicationIds(TENANT_ID, mapItems(descriptors, ApplicationDescriptor::getId)))
        .thenReturn(entitlements);

      var result = collector.collectRequiredAndProvided(descriptors, TENANT_ID);

      assertThat(result).hasSameElementsAs(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void collectRequiredAndProvided_positive_emptyDescriptorList(List<ApplicationDescriptor> descriptors) {
      var result = collector.collectRequiredAndProvided(descriptors, TENANT_ID);
      assertThat(result).isEmpty();
    }

    private static Stream<Arguments> requiredInterfacesIncludedDataProvider() {
      return Stream.of(
        arguments("nothing entitled",
          List.of(APP_DESCRIPTOR1, APP_DESCRIPTOR2, APP_DESCRIPTOR3),
          emptyList(),
          REQUIRED_PROVIDED_INTERFACES
        ),
        arguments("app1/app2 entitled",
          List.of(APP_DESCRIPTOR1, APP_DESCRIPTOR2, APP_DESCRIPTOR3),
          List.of(ENTITLEMENT_APP1, ENTITLEMENT_APP2),
          REQUIRED_PROVIDED_INTERFACES
        ),
        arguments("all apps entitled",
          List.of(APP_DESCRIPTOR1, APP_DESCRIPTOR2, APP_DESCRIPTOR3),
          List.of(ENTITLEMENT_APP1, ENTITLEMENT_APP2, ENTITLEMENT_APP3),
          REQUIRED_PROVIDED_INTERFACES
        )
      );
    }
  }

  @Nested
  @DisplayName("CombinedApplicationInterfaceCollector::RequiredInterfacesExcluded")
  class ExcludeRequiredInterfacesOfInstalledAppsTest {

    private CombinedApplicationInterfaceCollector collector;

    @BeforeEach
    void setUp() {
      var required = new CollectedInterfaceSettings();
      required.setExcludeEntitled(true);
      var properties = new ApplicationInterfaceCollectorProperties();
      properties.setRequired(required);

      this.collector = new CombinedApplicationInterfaceCollector(entitlementCrudService, properties);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("requiredInterfacesExcludedDataProvider")
    void collectRequiredAndProvided_positive(@SuppressWarnings("unused") String testName,
      List<ApplicationDescriptor> descriptors, List<Entitlement> entitlements,
      List<RequiredProvidedInterfaces> expected) {
      when(entitlementCrudService.findByApplicationIds(TENANT_ID, mapItems(descriptors, ApplicationDescriptor::getId)))
        .thenReturn(entitlements);

      var result = collector.collectRequiredAndProvided(descriptors, TENANT_ID);

      assertThat(result).hasSameElementsAs(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void collectRequiredAndProvided_positive_emptyDescriptorList(List<ApplicationDescriptor> descriptors) {
      var result = collector.collectRequiredAndProvided(descriptors, TENANT_ID);
      assertThat(result).isEmpty();
    }

    private static Stream<Arguments> requiredInterfacesExcludedDataProvider() {
      return Stream.of(
        arguments("nothing entitled",
          List.of(APP_DESCRIPTOR1, APP_DESCRIPTOR2, APP_DESCRIPTOR3),
          emptyList(),
          REQUIRED_PROVIDED_INTERFACES
        ),
        arguments("app1/app2 entitled",
          List.of(APP_DESCRIPTOR1, APP_DESCRIPTOR2, APP_DESCRIPTOR3),
          List.of(ENTITLEMENT_APP1, ENTITLEMENT_APP2),
          List.of(
            new RequiredProvidedInterfaces(
              Set.of(
                itfItem("folio-module2-api", "1.0", "folio-app3-1.0.0"),
                itfItem("folio-module3-api", "1.0", "folio-app3-1.0.0")
              ),
              Map.of(
                "folio-module1-api",
                Set.of(itfItem("folio-module1-api", "1.0", "folio-app1-1.0.0")),
                "folio-module2-api",
                Set.of(itfItem("folio-module2-api", "1.0", "folio-app2-1.0.0")),
                "folio-module3-api",
                Set.of(itfItem("folio-module3-api", "1.0", "folio-app3-1.0.0"))
              )
            )
          )
        ),
        arguments("all apps entitled",
          List.of(APP_DESCRIPTOR1, APP_DESCRIPTOR2, APP_DESCRIPTOR3),
          List.of(ENTITLEMENT_APP1, ENTITLEMENT_APP2, ENTITLEMENT_APP3),
          List.of(
            new RequiredProvidedInterfaces(
              emptySet(),
              Map.of(
                "folio-module1-api",
                Set.of(itfItem("folio-module1-api", "1.0", "folio-app1-1.0.0")),
                "folio-module2-api",
                Set.of(itfItem("folio-module2-api", "1.0", "folio-app2-1.0.0")),
                "folio-module3-api",
                Set.of(itfItem("folio-module3-api", "1.0", "folio-app3-1.0.0"))
              )
            )
          )
        )
      );
    }
  }
}
