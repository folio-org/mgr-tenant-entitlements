package org.folio.entitlement.service;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.REVOKE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.MODULE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.UI_MODULE;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_NAME;
import static org.folio.entitlement.support.TestConstants.APPLICATION_VERSION;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.flowParameters;
import static org.folio.entitlement.support.TestValues.module;

import java.util.List;
import java.util.regex.Pattern;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.Module;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.RoutingEntry;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.ModuleDescriptorHolder;
import org.folio.entitlement.domain.model.ModulesSequence;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ModuleSequenceProviderTest {

  private static final String MOD_FOO_ID = "mod-foo-1.0.0";
  private static final String MOD_FOO_V2_ID = "mod-foo-2.0.0";
  private static final String MOD_BAR_ID = "mod-bar-1.0.0";
  private static final String UI_FOO_ID = "folio_foo-1.0.0";
  private static final String UI_BAR_ID = "folio_bar-2.0.0";

  @InjectMocks private ModuleSequenceProvider moduleSequenceProvider;

  @Test
  void getSequence_positive_entitlementRequest() {
    var applicationDescriptor = appDescriptor(List.of(fooModuleDesc()), emptyList());
    var flowParams = flowParameters(request(ENTITLE), applicationDescriptor);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParams, emptyMap());

    var sequence = moduleSequenceProvider.getSequence(stageContext, MODULE);

    var expectedLayer = List.of(new ModuleDescriptorHolder(fooModuleDesc(), null));
    assertThat(sequence).isEqualTo(new ModulesSequence(List.of(expectedLayer), emptyList()));
  }

  @Test
  void getSequence_positive_entitlementRequestUiModule() {
    var applicationDescriptor = appDescriptor(emptyList(), List.of(uiFooModuleDesc(), uiBarModuleDesc()));
    var flowParams = flowParameters(request(ENTITLE), applicationDescriptor);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParams, emptyMap());

    var sequence = moduleSequenceProvider.getSequence(stageContext, UI_MODULE);

    var uiFooHolder = new ModuleDescriptorHolder(uiFooModuleDesc(), null);
    var uiBarHolder = new ModuleDescriptorHolder(uiBarModuleDesc(), null);
    var expectedLayer = List.of(uiBarHolder, uiFooHolder);
    assertThat(sequence).isEqualTo(new ModulesSequence(List.of(expectedLayer), emptyList()));
  }

  @Test
  void getSequence_positive_entitlementRequestMultipleModules() {
    var applicationDescriptor = appDescriptor(List.of(fooModuleDesc(), barModuleDesc()), emptyList());
    var flowParams = flowParameters(request(ENTITLE), applicationDescriptor);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParams, emptyMap());

    var sequence = moduleSequenceProvider.getSequence(stageContext, MODULE);

    var expectedLayer1 = List.of(new ModuleDescriptorHolder(fooModuleDesc(), null));
    var expectedLayer2 = List.of(new ModuleDescriptorHolder(barModuleDesc(), null));
    assertThat(sequence).isEqualTo(new ModulesSequence(List.of(expectedLayer1, expectedLayer2), emptyList()));
  }

  @Test
  void getSequence_positive_upgradeRequest() {
    var applicationDescriptor = appDescriptor(List.of(fooModuleDescV2()), emptyList());
    var entitledApplicationDescriptor = appDescriptor(List.of(fooModuleDesc()), emptyList());
    var flowParams = flowParameters(request(ENTITLE), applicationDescriptor, entitledApplicationDescriptor);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParams, emptyMap());

    var sequence = moduleSequenceProvider.getSequence(stageContext, MODULE);

    var expectedLayer = List.of(new ModuleDescriptorHolder(fooModuleDescV2(), fooModuleDesc()));
    assertThat(sequence).isEqualTo(new ModulesSequence(List.of(expectedLayer), emptyList()));
  }

  @Test
  void getSequence_positive_upgradeRequestWithNewModule() {
    var applicationDescriptor = appDescriptor(List.of(fooModuleDesc(), barModuleDesc()), emptyList());
    var entitledApplicationDescriptor = appDescriptor(List.of(fooModuleDesc()), emptyList());
    var flowParams = flowParameters(request(ENTITLE), applicationDescriptor, entitledApplicationDescriptor);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParams, emptyMap());

    var sequence = moduleSequenceProvider.getSequence(stageContext, MODULE);

    var expectedLayer1 = List.of(new ModuleDescriptorHolder(fooModuleDesc(), fooModuleDesc()));
    var expectedLayer2 = List.of(new ModuleDescriptorHolder(barModuleDesc(), null));
    assertThat(sequence).isEqualTo(new ModulesSequence(List.of(expectedLayer1, expectedLayer2), emptyList()));
  }

  @Test
  void getSequence_positive_upgradeRequestWithDeprecatedModule() {
    var applicationDescriptor = appDescriptor(List.of(fooModuleDesc()), emptyList());
    var entitledApplicationDescriptor = appDescriptor(List.of(fooModuleDesc(), barModuleDesc()), emptyList());
    var flowParams = flowParameters(request(ENTITLE), applicationDescriptor, entitledApplicationDescriptor);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParams, emptyMap());

    var sequence = moduleSequenceProvider.getSequence(stageContext, MODULE);

    var expectedLayer = List.of(new ModuleDescriptorHolder(fooModuleDesc(), fooModuleDesc()));
    var deprecatedModuleLayer = List.of(barModuleDesc());
    assertThat(sequence).isEqualTo(new ModulesSequence(List.of(expectedLayer), List.of(deprecatedModuleLayer)));
  }

  @Test
  void getSequence_positive_revokeRequestMultipleModules() {
    var applicationDescriptor = appDescriptor(List.of(fooModuleDesc(), barModuleDesc()), emptyList());
    var flowParams = flowParameters(request(REVOKE), applicationDescriptor);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParams, emptyMap());

    var sequence = moduleSequenceProvider.getSequence(stageContext, MODULE);

    var expectedLayer1 = List.of(new ModuleDescriptorHolder(barModuleDesc(), null));
    var expectedLayer2 = List.of(new ModuleDescriptorHolder(fooModuleDesc(), null));
    assertThat(sequence).isEqualTo(new ModulesSequence(List.of(expectedLayer1, expectedLayer2), emptyList()));
  }

  private static EntitlementRequest request(EntitlementRequestType type) {
    return EntitlementRequest.builder()
      .applications(List.of(APPLICATION_ID))
      .tenantId(TENANT_ID)
      .type(type)
      .build();
  }

  private static ModuleDescriptor fooModuleDesc() {
    return new ModuleDescriptor()
      .id(MOD_FOO_ID)
      .description("Foo module")
      .provides(List.of(new InterfaceDescriptor().id("foo-api").version("1.0").handlers(List.of(
        new RoutingEntry().methods(List.of("GET")).pathPattern("/foo-api/items")))));
  }

  private static ModuleDescriptor uiFooModuleDesc() {
    return new ModuleDescriptor()
      .id(UI_FOO_ID)
      .description("UI foo module")
      .requires(List.of(new InterfaceReference().id("foo-api").version("1.0")));
  }

  private static ModuleDescriptor uiBarModuleDesc() {
    return new ModuleDescriptor()
      .id(UI_BAR_ID)
      .description("UI bar module")
      .requires(List.of(new InterfaceReference().id("foo-api").version("2.0")));
  }

  private static ModuleDescriptor fooModuleDescV2() {
    return new ModuleDescriptor()
      .id(MOD_FOO_V2_ID)
      .description("Foo module name")
      .provides(List.of(new InterfaceDescriptor().id("foo-api").version("2.0").handlers(List.of(
        new RoutingEntry().methods(List.of("GET")).pathPattern("/foo-api/v2/items")))));
  }

  private static ModuleDescriptor barModuleDesc() {
    return new ModuleDescriptor()
      .id(MOD_BAR_ID)
      .description("Bar module")
      .requires(List.of(new InterfaceReference().id("foo-api").version("1.0")))
      .provides(List.of(new InterfaceDescriptor().id("bar-api").version("1.0").handlers(List.of(
        new RoutingEntry().methods(List.of("GET")).pathPattern("/bar-api/items")))));
  }

  private static ApplicationDescriptor appDescriptor(
    List<ModuleDescriptor> moduleDescriptors, List<ModuleDescriptor> uiModuleDescriptors) {
    return new ApplicationDescriptor()
      .id(APPLICATION_ID)
      .name(APPLICATION_NAME)
      .version(APPLICATION_VERSION)
      .modules(mapItems(moduleDescriptors, ModuleSequenceProviderTest::toModule))
      .uiModules(mapItems(uiModuleDescriptors, ModuleSequenceProviderTest::toModule))
      .moduleDescriptors(moduleDescriptors)
      .uiModuleDescriptors(uiModuleDescriptors);
  }

  private static Module toModule(ModuleDescriptor moduleDescriptor) {
    var pattern = Pattern.compile("(.+)-(\\d\\.\\d\\.\\d.*)");
    var matcher = pattern.matcher(moduleDescriptor.getId());
    assertThat(matcher.matches()).isTrue();
    return module(matcher.group(1), matcher.group(2));
  }
}
