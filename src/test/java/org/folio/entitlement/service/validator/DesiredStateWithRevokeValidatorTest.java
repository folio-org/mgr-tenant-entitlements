package org.folio.entitlement.service.validator;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.verifyNoMoreInteractions;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.model.ApplicationStateTransitionPlan;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DesiredStateWithRevokeValidatorTest {

  private static final String APP1_ID = "app1-1.0.0";
  private static final String APP2_ID = "app2-1.0.0";
  private static final String APP3_ID = "app3-1.0.0";

  @InjectMocks private DesiredStateWithRevokeValidator validator;
  @Mock private EntitlementCrudService entitlementCrudService;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_revokeBucketWithAllEntitlementsFound() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), emptySet(), Set.of(APP1_ID, APP2_ID));
    var context = createContext(plan);
    var entitlements = List.of(
      createEntitlement(APP1_ID),
      createEntitlement(APP2_ID)
    );

    when(entitlementCrudService.findByApplicationIds(TENANT_ID, Set.of(APP1_ID, APP2_ID)))
      .thenReturn(entitlements);

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_revokeBucketWithSingleEntitlementFound() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), emptySet(), Set.of(APP1_ID));
    var context = createContext(plan);
    var entitlements = List.of(createEntitlement(APP1_ID));

    when(entitlementCrudService.findByApplicationIds(TENANT_ID, Set.of(APP1_ID)))
      .thenReturn(entitlements);

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_allBucketsEmpty() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), emptySet(), emptySet());
    var context = createContext(plan);

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @NullAndEmptySource
  void execute_positive_revokeBucketNullWithOtherBucketsNonEmpty(Set<String> appIds) {
    var plan = ApplicationStateTransitionPlan.of(Set.of(APP1_ID), Set.of(APP2_ID), appIds);
    var context = createContext(plan);

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_negative_revokeBucketWithMissingEntitlement() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), emptySet(), Set.of(APP1_ID, APP2_ID));
    var context = createContext(plan);
    var entitlements = List.of(createEntitlement(APP1_ID));

    when(entitlementCrudService.findByApplicationIds(TENANT_ID, Set.of(APP1_ID, APP2_ID)))
      .thenReturn(entitlements);

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage("Entitlements are not found for applications: [app2-1.0.0]");
  }

  @Test
  void execute_negative_revokeBucketWithMultipleMissingEntitlements() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), emptySet(), Set.of(APP1_ID, APP2_ID, APP3_ID));
    var context = createContext(plan);
    var entitlements = List.of(createEntitlement(APP1_ID));

    when(entitlementCrudService.findByApplicationIds(TENANT_ID, Set.of(APP1_ID, APP2_ID, APP3_ID)))
      .thenReturn(entitlements);

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessageContaining("Entitlements are not found for applications:")
      .hasMessageContaining("app2-1.0.0")
      .hasMessageContaining("app3-1.0.0");
  }

  @Test
  void execute_negative_revokeBucketWithAllEntitlementsMissing() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), emptySet(), Set.of(APP1_ID, APP2_ID));
    var context = createContext(plan);

    when(entitlementCrudService.findByApplicationIds(TENANT_ID, Set.of(APP1_ID, APP2_ID)))
      .thenReturn(Collections.emptyList());

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessageContaining("Entitlements are not found for applications:")
      .hasMessageContaining("app1-1.0.0")
      .hasMessageContaining("app2-1.0.0");
  }

  private static CommonStageContext createContext(ApplicationStateTransitionPlan plan) {
    var request = EntitlementRequest.builder()
      .type(EntitlementRequestType.STATE)
      .tenantId(TENANT_ID)
      .build();

    var flowParams = Map.of(PARAM_REQUEST, request);
    var context = commonStageContext(FLOW_ID, flowParams, Collections.emptyMap());
    context.withApplicationStateTransitionPlan(plan);
    return context;
  }

  private static Entitlement createEntitlement(String applicationId) {
    return new Entitlement()
      .applicationId(applicationId)
      .tenantId(TENANT_ID);
  }
}
