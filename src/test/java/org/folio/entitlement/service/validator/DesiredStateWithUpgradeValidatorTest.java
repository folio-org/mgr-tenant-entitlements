package org.folio.entitlement.service.validator;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.verifyNoMoreInteractions;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.model.ApplicationStateTransitionPlan;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
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
class DesiredStateWithUpgradeValidatorTest {

  private static final String APP1_NAME = "app1";
  private static final String APP1_V1_ID = "app1-1.0.0";
  private static final String APP1_V2_ID = "app1-2.0.0";
  private static final String APP1_V3_ID = "app1-3.0.0";
  private static final String APP2_V1_ID = "app2-1.0.0";
  private static final String APP2_V2_ID = "app2-2.0.0";

  @InjectMocks private DesiredStateWithUpgradeValidator validator;
  @Mock private EntitlementCrudService entitlementCrudService;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_upgradeBucketWithValidUpgrade() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), Set.of(APP1_V2_ID), emptySet());
    var context = createContext(plan);
    var entitlements = List.of(createEntitlement(APP1_V1_ID));

    when(entitlementCrudService.findByApplicationNames(TENANT_ID, List.of(APP1_NAME)))
      .thenReturn(entitlements);

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_upgradeBucketWithMultipleValidUpgrades() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), Set.of(APP1_V2_ID, APP2_V2_ID), emptySet());
    var context = createContext(plan);
    var entitlements = List.of(
      createEntitlement(APP1_V1_ID),
      createEntitlement(APP2_V1_ID)
    );

    when(entitlementCrudService.findByApplicationNames(eq(TENANT_ID), any()))
      .thenReturn(entitlements);

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @NullAndEmptySource
  void execute_positive_upgradeBucketEmpty(Set<String> appIds) {
    var plan = ApplicationStateTransitionPlan.of(Set.of(APP1_V1_ID), appIds, emptySet());
    var context = createContext(plan);

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @NullAndEmptySource
  void execute_positive_upgradeBucketEmptyWithOtherBucketsNonEmpty(Set<String> appIds) {
    var plan = ApplicationStateTransitionPlan.of(Set.of(APP1_V1_ID), appIds, Set.of(APP2_V1_ID));
    var context = createContext(plan);

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_allBucketsEmpty() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), emptySet(), emptySet());
    var context = createContext(plan);

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_upgradeBucketWithHigherVersion() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), Set.of(APP1_V3_ID), emptySet());
    var context = createContext(plan);
    var entitlements = List.of(createEntitlement(APP1_V1_ID));

    when(entitlementCrudService.findByApplicationNames(TENANT_ID, List.of(APP1_NAME)))
      .thenReturn(entitlements);

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_negative_upgradeBucketWithMissingEntitlement() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), Set.of(APP1_V2_ID), emptySet());
    var context = createContext(plan);

    when(entitlementCrudService.findByApplicationNames(TENANT_ID, List.of(APP1_NAME)))
      .thenReturn(Collections.emptyList());

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid applications provided for upgrade")
      .satisfies(err -> {
        var exception = (RequestValidationException) err;
        var param = exception.getErrorParameters().getFirst();

        assertThat(param.getKey()).isEqualTo(APP1_V2_ID);
        assertThat(param.getValue()).isEqualTo("Entitlement is not found for application");
      });
  }

  @Test
  void execute_negative_upgradeBucketWithSameVersion() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), Set.of(APP1_V1_ID), emptySet());
    var context = createContext(plan);
    var entitlements = List.of(createEntitlement(APP1_V1_ID));

    when(entitlementCrudService.findByApplicationNames(TENANT_ID, List.of(APP1_NAME)))
      .thenReturn(entitlements);

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid applications provided for upgrade")
      .satisfies(err -> {
        var exception = (RequestValidationException) err;
        var params = exception.getErrorParameters().getFirst();

        assertThat(params.getKey()).isEqualTo(APP1_V1_ID);
        assertThat(params.getValue()).isEqualTo("Application version is same or lower than entitled");
      });
  }

  @Test
  void execute_negative_upgradeBucketWithLowerVersion() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), Set.of(APP1_V1_ID), emptySet());
    var context = createContext(plan);
    var entitlements = List.of(createEntitlement(APP1_V2_ID));

    when(entitlementCrudService.findByApplicationNames(TENANT_ID, List.of(APP1_NAME)))
      .thenReturn(entitlements);

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid applications provided for upgrade")
      .satisfies(err -> {
        var exception = (RequestValidationException) err;
        var param = exception.getErrorParameters().getFirst();

        assertThat(param.getKey()).isEqualTo(APP1_V1_ID);
        assertThat(param.getValue()).isEqualTo("Application version is same or lower than entitled");
      });
  }

  @Test
  void execute_negative_upgradeBucketWithInvalidApplicationId() {
    var invalidAppId = "invalid-app-id";
    var plan = ApplicationStateTransitionPlan.of(emptySet(), Set.of(invalidAppId), emptySet());
    var context = createContext(plan);

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid applications provided for upgrade")
      .satisfies(err -> {
        var exception = (RequestValidationException) err;
        var param = exception.getErrorParameters().getFirst();

        assertThat(param.getKey()).isEqualTo("details");
        assertThat(param.getValue()).isEqualTo("Invalid semantic version: source = " + invalidAppId);
      });
  }

  @Test
  void execute_negative_upgradeBucketWithMultipleErrors() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), Set.of(APP1_V2_ID, APP2_V1_ID), emptySet());
    var context = createContext(plan);
    var entitlements = List.of(createEntitlement(APP2_V2_ID));

    when(entitlementCrudService.findByApplicationNames(eq(TENANT_ID), any()))
      .thenReturn(entitlements);

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid applications provided for upgrade")
      .satisfies(err -> {
        var exception = (RequestValidationException) err;
        var params = exception.getErrorParameters();
        assertThatCode(() -> {
          params.stream()
            .filter(p -> APP1_V2_ID.equals(p.getKey()))
            .filter(p -> "Entitlement is not found for application".equals(p.getValue()))
            .findFirst()
            .orElseThrow();
          params.stream()
            .filter(p -> APP2_V1_ID.equals(p.getKey()))
            .filter(p -> "Application version is same or lower than entitled".equals(p.getValue()))
            .findFirst()
            .orElseThrow();
        }).doesNotThrowAnyException();
      });
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
