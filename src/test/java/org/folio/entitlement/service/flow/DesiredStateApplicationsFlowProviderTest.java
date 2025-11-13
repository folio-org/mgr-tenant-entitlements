package org.folio.entitlement.service.flow;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.apache.commons.collections4.ListUtils.union;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.verifyNoMoreInteractions;
import static org.folio.entitlement.support.TestValues.appDescriptor;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DesiredStateApplicationsFlowProviderTest {

  private static final String APP_FOO_ID = "app-foo-1.0.0";
  private static final String APP_BAR_ID = "app-bar-1.0.0";
  private static final String APP_BAZ_ID = "app-baz-1.0.0";

  @InjectMocks private DesiredStateApplicationsFlowProvider provider;
  @Mock private LayerFlowProvider layerFlowProvider;
  @Mock private Stage<StageContext> mockStage1;
  @Mock private Stage<StageContext> mockStage2;
  @Mock private Stage<StageContext> mockStage3;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(this);
  }

  @Test
  void getName_positive() {
    var result = provider.getName();

    assertThat(result).isEqualTo(DesiredStateApplicationsFlowProvider.class.getSimpleName());
  }

  @Test
  void createApplicationFlows_positive_revokeOnly() {
    var descriptorsByType = Map.of(
      REVOKE, List.of(appDescriptor(APP_FOO_ID))
    );
    var context = createContext(descriptorsByType);
    var revokeFlows = List.<Stage<? extends StageContext>>of(mockStage1);

    when(layerFlowProvider.prepareLayeredFlowsReversed(eq(context), argThat(list ->
      list.size() == 1 && list.getFirst().type() == REVOKE)))
      .thenReturn(revokeFlows);
    when(layerFlowProvider.prepareLayeredFlows(eq(context), argThat(List::isEmpty)))
      .thenReturn(emptyList());

    var result = provider.createApplicationFlows(context);

    assertThat(result).isEqualTo(revokeFlows);
  }

  @Test
  void createApplicationFlows_positive_entitleOnly() {
    var descriptorsByType = Map.of(
      ENTITLE, List.of(appDescriptor(APP_FOO_ID))
    );
    var context = createContext(descriptorsByType);
    var entitleFlows = List.<Stage<? extends StageContext>>of(mockStage1);

    when(layerFlowProvider.prepareLayeredFlowsReversed(eq(context), argThat(List::isEmpty)))
      .thenReturn(emptyList());
    when(layerFlowProvider.prepareLayeredFlows(eq(context), argThat(list ->
      list.size() == 1 && list.getFirst().type() == ENTITLE)))
      .thenReturn(entitleFlows);

    var result = provider.createApplicationFlows(context);

    assertThat(result).isEqualTo(entitleFlows);
  }

  @Test
  void createApplicationFlows_positive_upgradeOnly() {
    var descriptorsByType = Map.of(
      UPGRADE, List.of(appDescriptor(APP_FOO_ID))
    );
    var context = createContext(descriptorsByType);
    var upgradeFlows = List.<Stage<? extends StageContext>>of(mockStage1);

    when(layerFlowProvider.prepareLayeredFlowsReversed(eq(context), argThat(List::isEmpty)))
      .thenReturn(emptyList());
    when(layerFlowProvider.prepareLayeredFlows(eq(context), argThat(list ->
      list.size() == 1 && list.getFirst().type() == UPGRADE)))
      .thenReturn(upgradeFlows);

    var result = provider.createApplicationFlows(context);

    assertThat(result).isEqualTo(upgradeFlows);
  }

  @Test
  void createApplicationFlows_positive_entitleAndUpgrade() {
    var descriptorsByType = Map.of(
      ENTITLE, List.of(appDescriptor(APP_FOO_ID)),
      UPGRADE, List.of(appDescriptor(APP_BAR_ID))
    );
    var context = createContext(descriptorsByType);
    var combinedFlows = List.<Stage<? extends StageContext>>of(mockStage1, mockStage2);

    when(layerFlowProvider.prepareLayeredFlowsReversed(eq(context), argThat(List::isEmpty)))
      .thenReturn(emptyList());
    when(layerFlowProvider.prepareLayeredFlows(eq(context), argThat(list ->
      list.size() == 2
        && list.stream().anyMatch(e -> e.type() == ENTITLE)
        && list.stream().anyMatch(e -> e.type() == UPGRADE))))
      .thenReturn(combinedFlows);

    var result = provider.createApplicationFlows(context);

    assertThat(result).isEqualTo(combinedFlows);
  }

  @Test
  void createApplicationFlows_positive_allTypes() {
    var descriptorsByType = Map.of(
      REVOKE, List.of(appDescriptor(APP_FOO_ID)),
      ENTITLE, List.of(appDescriptor(APP_BAR_ID)),
      UPGRADE, List.of(appDescriptor(APP_BAZ_ID))
    );
    var context = createContext(descriptorsByType);
    var revokeFlows = List.<Stage<? extends StageContext>>of(mockStage1);
    var combinedFlows = List.<Stage<? extends StageContext>>of(mockStage2, mockStage3);

    when(layerFlowProvider.prepareLayeredFlowsReversed(eq(context), argThat(list ->
      list.size() == 1 && list.get(0).type() == REVOKE)))
      .thenReturn(revokeFlows);
    when(layerFlowProvider.prepareLayeredFlows(eq(context), argThat(list ->
      list.size() == 2
        && list.stream().anyMatch(e -> e.type() == ENTITLE)
        && list.stream().anyMatch(e -> e.type() == UPGRADE))))
      .thenReturn(combinedFlows);

    var result = provider.createApplicationFlows(context);

    assertThat(result).isEqualTo(union(revokeFlows, combinedFlows));
  }

  @Test
  void createApplicationFlows_positive_revokeAndEntitle() {
    var descriptorsByType = Map.of(
      REVOKE, List.of(appDescriptor(APP_FOO_ID)),
      ENTITLE, List.of(appDescriptor(APP_BAR_ID))
    );
    var context = createContext(descriptorsByType);
    var revokeFlows = List.<Stage<? extends StageContext>>of(mockStage1);
    var entitleFlows = List.<Stage<? extends StageContext>>of(mockStage2);

    when(layerFlowProvider.prepareLayeredFlowsReversed(eq(context), argThat(list ->
      list.size() == 1 && list.get(0).type() == REVOKE)))
      .thenReturn(revokeFlows);
    when(layerFlowProvider.prepareLayeredFlows(eq(context), argThat(list ->
      list.size() == 1 && list.get(0).type() == ENTITLE)))
      .thenReturn(entitleFlows);

    var result = provider.createApplicationFlows(context);

    assertThat(result).isEqualTo(union(revokeFlows, entitleFlows));
  }

  @Test
  void createApplicationFlows_positive_multipleApplicationsPerType() {
    var descriptorsByType = Map.of(
      REVOKE, List.of(appDescriptor(APP_FOO_ID), appDescriptor(APP_BAR_ID)),
      ENTITLE, List.of(appDescriptor(APP_BAZ_ID))
    );
    var context = createContext(descriptorsByType);
    var revokeFlows = List.<Stage<? extends StageContext>>of(mockStage1);
    var entitleFlows = List.<Stage<? extends StageContext>>of(mockStage2);

    when(layerFlowProvider.prepareLayeredFlowsReversed(eq(context), argThat(list ->
      list.size() == 2 && list.stream().allMatch(e -> e.type() == REVOKE))))
      .thenReturn(revokeFlows);
    when(layerFlowProvider.prepareLayeredFlows(eq(context), argThat(list ->
      list.size() == 1 && list.getFirst().type() == ENTITLE)))
      .thenReturn(entitleFlows);

    var result = provider.createApplicationFlows(context);

    assertThat(result).isEqualTo(union(revokeFlows, entitleFlows));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("emptyDescriptorsDataProvider")
  void createApplicationFlows_positive_emptyDescriptors(@SuppressWarnings("unused") String testName,
    Map<EntitlementType, List<ApplicationDescriptor>> descriptorsByType) {
    var context = createContext(descriptorsByType);

    when(layerFlowProvider.prepareLayeredFlowsReversed(eq(context), argThat(List::isEmpty)))
      .thenReturn(emptyList());
    when(layerFlowProvider.prepareLayeredFlows(eq(context), argThat(List::isEmpty)))
      .thenReturn(emptyList());

    var result = provider.createApplicationFlows(context);

    assertThat(result).isEmpty();
  }

  private static Stream<Arguments> emptyDescriptorsDataProvider() {
    return Stream.of(
      arguments("empty map", emptyMap()),
      arguments("all types with empty lists", Map.of(
        REVOKE, emptyList(),
        ENTITLE, emptyList(),
        UPGRADE, emptyList()
      ))
    );
  }

  private static CommonStageContext createContext(Map<EntitlementType, List<ApplicationDescriptor>> descriptorsByType) {
    var request = EntitlementRequest.builder()
      .type(EntitlementRequestType.STATE)
      .tenantId(TENANT_ID)
      .build();

    var flowParams = Map.of(PARAM_REQUEST, request);
    var context = commonStageContext(FLOW_ID, flowParams, emptyMap());
    context.withApplicationStateTransitionDescriptors(descriptorsByType);
    return context;
  }
}
