package org.folio.entitlement.service.validator;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class UpgradeRequestValidatorTest {
  private static final String APP_FOO_V1 = "app-foo-1.0.0";
  private static final String APP_FOO_V2 = "app-foo-2.0.0";
  private static final String APP_BAR_V1 = "app-bar-1.0.0";
  private static final String APP_BAR_V2 = "app-bar-2.0.0";

  @InjectMocks private UpgradeRequestValidator upgradeRequestValidator;
  @Mock private EntitlementCrudService entitlementService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive() {
    var applicationIds = List.of(APP_FOO_V2, APP_BAR_V2);
    var entitlementRequest = entitlementRequest(applicationIds);

    var applicationNames = List.of("app-foo", "app-bar");
    var entitlements = List.of(entitlement(APP_FOO_V1), entitlement(APP_BAR_V1));
    when(entitlementService.findByApplicationNames(TENANT_ID, applicationNames)).thenReturn(entitlements);

    var flowParams = Map.of(PARAM_REQUEST, entitlementRequest);
    var stageContext = commonStageContext(FLOW_ID, flowParams, emptyMap());
    upgradeRequestValidator.execute(stageContext);

    assertThat(stageContext.getEntitledApplicationIds()).containsExactly(APP_FOO_V1, APP_BAR_V1);
  }

  @Test
  void execute_positive_snapshotVersionUpgrade() {
    var oldVersion = "app-foo-1.0.0-SNAPSHOT.1";
    var newVersion = "app-foo-1.0.0-SNAPSHOT.2";
    var applicationIds = List.of(newVersion);
    var entitlementRequest = entitlementRequest(applicationIds);
    var entitlements = List.of(entitlement(oldVersion));

    when(entitlementService.findByApplicationNames(TENANT_ID, List.of("app-foo"))).thenReturn(entitlements);

    var flowParams = Map.of(PARAM_REQUEST, entitlementRequest);
    var stageContext = commonStageContext(FLOW_ID, flowParams, emptyMap());
    upgradeRequestValidator.execute(stageContext);

    assertThat(stageContext.getEntitledApplicationIds()).containsExactly(oldVersion);
  }

  @Test
  void execute_positive_preReleaseSnapshotVersionUpgrade() {
    var oldVersion = "app-foo-0.0.1-SNAPSHOT.1";
    var newVersion = "app-foo-0.0.1-SNAPSHOT.2";
    var entitlementRequest = entitlementRequest(List.of(newVersion));
    var entitlements = List.of(entitlement(oldVersion));

    when(entitlementService.findByApplicationNames(TENANT_ID, List.of("app-foo"))).thenReturn(entitlements);

    var flowParams = Map.of(PARAM_REQUEST, entitlementRequest);
    var stageContext = commonStageContext(FLOW_ID, flowParams, emptyMap());
    upgradeRequestValidator.execute(stageContext);

    assertThat(stageContext.getEntitledApplicationIds()).containsExactly(oldVersion);
  }

  @Test
  void execute_negative_versionNotChangedForOneOfApplications() {
    var applicationIds = List.of(APP_FOO_V2, APP_BAR_V1);
    var entitlementRequest = entitlementRequest(applicationIds);

    var applicationNames = List.of("app-foo", "app-bar");
    var entitlements = List.of(entitlement(APP_FOO_V1), entitlement(APP_BAR_V1));
    when(entitlementService.findByApplicationNames(TENANT_ID, applicationNames)).thenReturn(entitlements);

    var flowParams = Map.of(PARAM_REQUEST, entitlementRequest);
    var stageContext = commonStageContext(FLOW_ID, flowParams, emptyMap());
    assertThatThrownBy(() -> upgradeRequestValidator.execute(stageContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid applications provided for upgrade")
      .extracting(error -> ((RequestValidationException) error).getErrorParameters())
      .satisfies(parameters -> assertThat(parameters).containsExactly(
        new Parameter().key(APP_BAR_V1).value("Application version is same or lower than entitled")));

    assertThat(stageContext.getEntitledApplicationIds()).isNull();
  }

  @Test
  void execute_negative_versionIsLowerForSnapshot() {
    var oldVersion = "app-foo-0.0.1-SNAPSHOT.22";
    var newVersion = "app-foo-0.0.1-SNAPSHOT.11";
    var entitlementRequest = entitlementRequest(List.of(newVersion));

    var entitlements = List.of(entitlement(oldVersion));
    when(entitlementService.findByApplicationNames(TENANT_ID, List.of("app-foo"))).thenReturn(entitlements);

    var flowParams = Map.of(PARAM_REQUEST, entitlementRequest);
    var stageContext = commonStageContext(FLOW_ID, flowParams, emptyMap());
    assertThatThrownBy(() -> upgradeRequestValidator.execute(stageContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid applications provided for upgrade")
      .extracting(error -> ((RequestValidationException) error).getErrorParameters())
      .satisfies(parameters -> assertThat(parameters).containsExactly(
        new Parameter().key(newVersion).value("Application version is same or lower than entitled")));

    assertThat(stageContext.getEntitledApplicationIds()).isNull();
  }

  @Test
  void execute_negative_previousVersionOfNotFound() {
    var entitlementRequest = entitlementRequest(List.of(APP_FOO_V2));
    when(entitlementService.findByApplicationNames(TENANT_ID, List.of("app-foo"))).thenReturn(emptyList());

    var flowParams = Map.of(PARAM_REQUEST, entitlementRequest);
    var stageContext = commonStageContext(FLOW_ID, flowParams, emptyMap());
    assertThatThrownBy(() -> upgradeRequestValidator.execute(stageContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid applications provided for upgrade")
      .extracting(error -> ((RequestValidationException) error).getErrorParameters())
      .satisfies(parameters -> assertThat(parameters).containsExactly(
        new Parameter().key(APP_FOO_V2).value("Entitlement is not found for application")));

    assertThat(stageContext.getEntitledApplicationIds()).isNull();
  }

  @Test
  void execute_negative_invalidApplicationVersion() {
    var applicationName = "app-foo-1.2";
    var entitlementRequest = entitlementRequest(List.of(applicationName));

    var flowParams = Map.of(PARAM_REQUEST, entitlementRequest);
    var stageContext = commonStageContext(FLOW_ID, flowParams, emptyMap());
    assertThatThrownBy(() -> upgradeRequestValidator.execute(stageContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid applications provided for upgrade")
      .extracting(error -> ((RequestValidationException) error).getErrorParameters())
      .satisfies(parameters -> assertThat(parameters).containsExactly(
        new Parameter().key("details").value("Invalid semantic version: source = " + applicationName)));

    assertThat(stageContext.getEntitledApplicationIds()).isNull();
  }

  @Test
  void execute_negative_invalidRequestApplicationVersion() {
    var applicationName = "app-foo-1.2.3";
    var entitlementRequest = entitlementRequest(List.of(applicationName));
    var entitlements = List.of(entitlement("app-foo"));
    when(entitlementService.findByApplicationNames(TENANT_ID, List.of("app-foo"))).thenReturn(entitlements);

    var flowParams = Map.of(PARAM_REQUEST, entitlementRequest);
    var stageContext = commonStageContext(FLOW_ID, flowParams, emptyMap());
    assertThatThrownBy(() -> upgradeRequestValidator.execute(stageContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid applications provided for upgrade")
      .extracting(error -> ((RequestValidationException) error).getErrorParameters())
      .satisfies(parameters -> assertThat(parameters).containsExactly(
        new Parameter().key("details").value("Invalid semantic version: source = app-foo")));

    assertThat(stageContext.getEntitledApplicationIds()).isNull();
  }

  @Test
  void validate_positive() {
    var applicationIds = List.of(APP_FOO_V2, APP_BAR_V2);
    var entitlementRequest = entitlementRequest(applicationIds);

    var applicationNames = List.of("app-foo", "app-bar");
    var entitlements = List.of(entitlement(APP_FOO_V1), entitlement(APP_BAR_V1));
    when(entitlementService.findByApplicationNames(TENANT_ID, applicationNames)).thenReturn(entitlements);

    upgradeRequestValidator.validate(entitlementRequest);

    verify(entitlementService).findByApplicationNames(TENANT_ID, applicationNames);
  }

  @Test
  void validate_positive_snapshotVersionUpgrade() {
    var oldVersion = "app-foo-1.0.0-SNAPSHOT.1";
    var newVersion = "app-foo-1.0.0-SNAPSHOT.2";
    var applicationIds = List.of(newVersion);
    var entitlementRequest = entitlementRequest(applicationIds);
    var entitlements = List.of(entitlement(oldVersion));
    var applicationNames = List.of("app-foo");

    when(entitlementService.findByApplicationNames(TENANT_ID, applicationNames)).thenReturn(entitlements);

    upgradeRequestValidator.validate(entitlementRequest);

    verify(entitlementService).findByApplicationNames(TENANT_ID, applicationNames);
  }

  @Test
  void validate_positive_preReleaseSnapshotVersionUpgrade() {
    var oldVersion = "app-foo-0.0.1-SNAPSHOT.1";
    var newVersion = "app-foo-0.0.1-SNAPSHOT.2";
    var entitlementRequest = entitlementRequest(List.of(newVersion));
    var entitlements = List.of(entitlement(oldVersion));

    var applicationNames = List.of("app-foo");
    when(entitlementService.findByApplicationNames(TENANT_ID, applicationNames)).thenReturn(entitlements);

    upgradeRequestValidator.validate(entitlementRequest);

    verify(entitlementService).findByApplicationNames(TENANT_ID, applicationNames);
  }

  @Test
  void validate_negative_versionNotChangedForOneOfApplications() {
    var applicationIds = List.of(APP_FOO_V2, APP_BAR_V1);
    var entitlementRequest = entitlementRequest(applicationIds);

    var applicationNames = List.of("app-foo", "app-bar");
    var entitlements = List.of(entitlement(APP_FOO_V1), entitlement(APP_BAR_V1));
    when(entitlementService.findByApplicationNames(TENANT_ID, applicationNames)).thenReturn(entitlements);

    assertThatThrownBy(() -> upgradeRequestValidator.validate(entitlementRequest))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid applications provided for upgrade")
      .extracting(error -> ((RequestValidationException) error).getErrorParameters())
      .satisfies(parameters -> assertThat(parameters).containsExactly(
        new Parameter().key(APP_BAR_V1).value("Application version is same or lower than entitled")));
  }

  @Test
  void validate_negative_versionIsLowerForSnapshot() {
    var oldVersion = "app-foo-0.0.1-SNAPSHOT.22";
    var newVersion = "app-foo-0.0.1-SNAPSHOT.11";
    var entitlementRequest = entitlementRequest(List.of(newVersion));

    var entitlements = List.of(entitlement(oldVersion));
    when(entitlementService.findByApplicationNames(TENANT_ID, List.of("app-foo"))).thenReturn(entitlements);

    assertThatThrownBy(() -> upgradeRequestValidator.validate(entitlementRequest))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid applications provided for upgrade")
      .extracting(error -> ((RequestValidationException) error).getErrorParameters())
      .satisfies(parameters -> assertThat(parameters).containsExactly(
        new Parameter().key(newVersion).value("Application version is same or lower than entitled")));
  }

  @Test
  void validate_negative_previousVersionOfNotFound() {
    var entitlementRequest = entitlementRequest(List.of(APP_FOO_V2));
    when(entitlementService.findByApplicationNames(TENANT_ID, List.of("app-foo"))).thenReturn(emptyList());

    assertThatThrownBy(() -> upgradeRequestValidator.validate(entitlementRequest))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid applications provided for upgrade")
      .extracting(error -> ((RequestValidationException) error).getErrorParameters())
      .satisfies(parameters -> assertThat(parameters).containsExactly(
        new Parameter().key(APP_FOO_V2).value("Entitlement is not found for application")));
  }

  @Test
  void validate_negative_invalidApplicationVersion() {
    var applicationName = "app-foo";
    var entitlementRequest = entitlementRequest(List.of(applicationName));

    assertThatThrownBy(() -> upgradeRequestValidator.validate(entitlementRequest))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid applications provided for upgrade")
      .extracting(error -> ((RequestValidationException) error).getErrorParameters())
      .satisfies(parameters -> assertThat(parameters).containsExactly(
        new Parameter().key("details").value("Invalid semantic version: source = " + applicationName)));
  }

  @Test
  void validate_negative_invalidRequestApplicationVersion() {
    var applicationName = "app-foo-1.2.3";
    var entitlementRequest = entitlementRequest(List.of(applicationName));
    var entitlements = List.of(entitlement("app-foo"));
    when(entitlementService.findByApplicationNames(TENANT_ID, List.of("app-foo"))).thenReturn(entitlements);

    assertThatThrownBy(() -> upgradeRequestValidator.validate(entitlementRequest))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Invalid applications provided for upgrade")
      .extracting(error -> ((RequestValidationException) error).getErrorParameters())
      .satisfies(parameters -> assertThat(parameters).containsExactly(
        new Parameter().key("details").value("Invalid semantic version: source = app-foo")));
  }

  @ParameterizedTest
  @DisplayName("shouldValidate_parameterized")
  @CsvSource({"ENTITLE, false", "REVOKE, false", "UPGRADE, true", ", false"})
  void shouldValidate_parameterized(EntitlementType type, boolean expected) {
    var request = EntitlementRequest.builder().type(type).build();
    var result = upgradeRequestValidator.shouldValidate(request);
    assertThat(result).isEqualTo(expected);
  }

  private static EntitlementRequest entitlementRequest(List<String> applicationIds) {
    return EntitlementRequest.builder()
      .type(UPGRADE)
      .tenantId(TENANT_ID)
      .applications(applicationIds)
      .build();
  }
}
