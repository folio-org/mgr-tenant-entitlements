package org.folio.entitlement.service.stage;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.domain.model.ApplicationStateTransitionBucket.entitle;
import static org.folio.entitlement.domain.model.ApplicationStateTransitionBucket.revoke;
import static org.folio.entitlement.domain.model.ApplicationStateTransitionBucket.upgrade;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.ENTITLED_APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.appDescriptor;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.folio.test.TestUtils.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.model.ApplicationStateTransitionBucket;
import org.folio.entitlement.domain.model.ApplicationStateTransitionPlan;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DesiredStateApplicationDescriptorLoaderTest {

  private static final String APPLICATION_V2_ID = "test-app-2.0.0";

  @InjectMocks private DesiredStateApplicationDescriptorLoader loader;
  @Mock private ApplicationManagerService applicationManagerService;
  @Mock private EntitlementCrudService entitlementService;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_entitleBucketOnly() {
    var descriptor = appDescriptor(APPLICATION_ID);
    var plan = createPlan(entitle(Set.of(APPLICATION_ID)), upgrade(emptySet()), revoke(emptySet()));
    var context = createContext(plan);

    when(applicationManagerService.getApplicationDescriptors(Set.of(APPLICATION_ID), OKAPI_TOKEN))
      .thenReturn(List.of(descriptor));

    loader.execute(context);

    var descriptorsByType = context.getApplicationStateTransitionDescriptors();
    assertThat(descriptorsByType).containsOnly(entry(ENTITLE, List.of(descriptor)));

    var requestDescriptors = context.getApplicationDescriptors();
    assertThat(requestDescriptors).containsOnly(descriptor);
  }

  @Test
  void execute_positive_revokeBucketOnly() {
    var descriptor = appDescriptor(APPLICATION_ID);
    var plan = createPlan(entitle(emptySet()), upgrade(emptySet()), revoke(Set.of(APPLICATION_ID)));
    var context = createContext(plan);

    when(applicationManagerService.getApplicationDescriptors(Set.of(APPLICATION_ID), OKAPI_TOKEN))
      .thenReturn(List.of(descriptor));

    loader.execute(context);

    var descriptorsByType = context.getApplicationStateTransitionDescriptors();
    assertThat(descriptorsByType).containsOnly(entry(REVOKE, List.of(descriptor)));

    var requestDescriptors = context.getApplicationDescriptors();
    assertThat(requestDescriptors).isEmpty();
  }

  @Test
  @SuppressWarnings("VariableDeclarationUsageDistance")
  void execute_positive_upgradeBucketOnly() {
    var descriptor = appDescriptor(APPLICATION_V2_ID);
    var entitledDescriptor = appDescriptor(ENTITLED_APPLICATION_ID);
    var entitlement = entitlement(ENTITLED_APPLICATION_ID);
    var plan = createPlan(entitle(emptySet()), upgrade(Set.of(APPLICATION_V2_ID)), revoke(emptySet()));
    var context = createContext(plan);

    when(applicationManagerService.getApplicationDescriptors(Set.of(APPLICATION_V2_ID), OKAPI_TOKEN))
      .thenReturn(List.of(descriptor));
    when(entitlementService.findByApplicationNames(TENANT_ID, List.of("test-app")))
      .thenReturn(List.of(entitlement));
    when(applicationManagerService.getApplicationDescriptors(List.of(ENTITLED_APPLICATION_ID), OKAPI_TOKEN))
      .thenReturn(List.of(entitledDescriptor));

    loader.execute(context);

    var descriptorsByType = context.getApplicationStateTransitionDescriptors();
    assertThat(descriptorsByType).containsOnly(entry(UPGRADE, List.of(descriptor)));

    var entitledAppIds = context.getEntitledApplicationIds();
    assertThat(entitledAppIds).containsExactly(ENTITLED_APPLICATION_ID);

    var entitledAppDescriptors = context.getEntitledApplicationDescriptors();
    assertThat(entitledAppDescriptors).containsExactly(entitledDescriptor);

    var requestDescriptors = context.getApplicationDescriptors();
    assertThat(requestDescriptors).containsOnly(descriptor);
  }

  @Test
  @SuppressWarnings("VariableDeclarationUsageDistance")
  void execute_positive_multipleBuckets() {
    var entitleDescriptor = appDescriptor(APPLICATION_ID);
    var revokeDescriptor = appDescriptor("revoke-app-1.0.0");
    var upgradeDescriptor = appDescriptor(APPLICATION_V2_ID);
    var entitledDescriptor = appDescriptor(ENTITLED_APPLICATION_ID);
    var entitlement = entitlement(ENTITLED_APPLICATION_ID);

    var plan = createPlan(entitle(Set.of(APPLICATION_ID)), upgrade(Set.of(APPLICATION_V2_ID)),
      revoke(Set.of("revoke-app-1.0.0")));
    var context = createContext(plan);

    when(applicationManagerService.getApplicationDescriptors(Set.of(APPLICATION_ID), OKAPI_TOKEN))
      .thenReturn(List.of(entitleDescriptor));
    when(applicationManagerService.getApplicationDescriptors(Set.of(APPLICATION_V2_ID), OKAPI_TOKEN))
      .thenReturn(List.of(upgradeDescriptor));
    when(applicationManagerService.getApplicationDescriptors(Set.of("revoke-app-1.0.0"), OKAPI_TOKEN))
      .thenReturn(List.of(revokeDescriptor));
    when(entitlementService.findByApplicationNames(TENANT_ID, List.of("test-app")))
      .thenReturn(List.of(entitlement));
    when(applicationManagerService.getApplicationDescriptors(List.of(ENTITLED_APPLICATION_ID), OKAPI_TOKEN))
      .thenReturn(List.of(entitledDescriptor));

    loader.execute(context);

    var descriptorsByType = context.getApplicationStateTransitionDescriptors();
    assertThat(descriptorsByType).containsOnly(
      entry(ENTITLE, List.of(entitleDescriptor)),
      entry(UPGRADE, List.of(upgradeDescriptor)),
      entry(REVOKE, List.of(revokeDescriptor)));

    var entitledAppIds = context.getEntitledApplicationIds();
    assertThat(entitledAppIds).containsExactly(ENTITLED_APPLICATION_ID);

    var entitledAppDescriptors = context.getEntitledApplicationDescriptors();
    assertThat(entitledAppDescriptors).containsExactly(entitledDescriptor);

    var requestDescriptors = context.getApplicationDescriptors();
    assertThat(requestDescriptors).containsOnly(entitleDescriptor, upgradeDescriptor);
  }

  @Test
  void execute_positive_allBucketsEmpty() {
    var plan = createPlan(entitle(emptySet()), upgrade(emptySet()), revoke(emptySet()));
    var context = createContext(plan);

    loader.execute(context);

    var descriptorsByType = context.getApplicationStateTransitionDescriptors();
    assertThat(descriptorsByType).isEmpty();

    var requestDescriptors = context.getApplicationDescriptors();
    assertThat(requestDescriptors).isEmpty();
  }

  @Test
  @SuppressWarnings("VariableDeclarationUsageDistance")
  void execute_positive_multipleEntitlementsForUpgrade() {
    var descriptor = appDescriptor(APPLICATION_V2_ID);
    var entitlement1 = entitlement("test-app-0.0.8");
    var entitlement2 = entitlement(ENTITLED_APPLICATION_ID);
    var entitledDescriptor1 = appDescriptor("test-app-0.0.8");
    var entitledDescriptor2 = appDescriptor(ENTITLED_APPLICATION_ID);

    var plan = createPlan(entitle(emptySet()), upgrade(Set.of(APPLICATION_V2_ID)), revoke(emptySet()));
    var context = createContext(plan);

    when(applicationManagerService.getApplicationDescriptors(Set.of(APPLICATION_V2_ID), OKAPI_TOKEN))
      .thenReturn(List.of(descriptor));
    when(entitlementService.findByApplicationNames(TENANT_ID, List.of("test-app")))
      .thenReturn(List.of(entitlement1, entitlement2));
    when(applicationManagerService.getApplicationDescriptors(
      List.of("test-app-0.0.8", ENTITLED_APPLICATION_ID), OKAPI_TOKEN))
      .thenReturn(List.of(entitledDescriptor1, entitledDescriptor2));

    loader.execute(context);

    var entitledAppIds = context.getEntitledApplicationIds();
    assertThat(entitledAppIds).containsExactlyInAnyOrder("test-app-0.0.8", ENTITLED_APPLICATION_ID);

    var entitledAppDescriptors = context.getEntitledApplicationDescriptors();
    assertThat(entitledAppDescriptors).containsExactlyInAnyOrder(entitledDescriptor1, entitledDescriptor2);

    var requestDescriptors = context.getApplicationDescriptors();
    assertThat(requestDescriptors).containsOnly(descriptor);
  }

  private static CommonStageContext createContext(ApplicationStateTransitionPlan plan) {
    var request = EntitlementRequest.builder()
      .type(EntitlementRequestType.STATE)
      .tenantId(TENANT_ID)
      .okapiToken(OKAPI_TOKEN)
      .build();

    var flowParams = Map.of(PARAM_REQUEST, request);
    var context = commonStageContext(FLOW_STAGE_ID, flowParams, Collections.emptyMap());
    context.withApplicationStateTransitionPlan(plan);
    return context;
  }

  private static ApplicationStateTransitionPlan createPlan(
    ApplicationStateTransitionBucket entitleBucket,
    ApplicationStateTransitionBucket upgradeBucket,
    ApplicationStateTransitionBucket revokeBucket) {
    return new ApplicationStateTransitionPlan(entitleBucket, upgradeBucket, revokeBucket);
  }
}
