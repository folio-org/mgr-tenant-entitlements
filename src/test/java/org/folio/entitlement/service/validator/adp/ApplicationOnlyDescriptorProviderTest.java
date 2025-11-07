package org.folio.entitlement.service.validator.adp;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_APP_DESCRIPTORS;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.verifyNoMoreInteractions;
import static org.folio.entitlement.support.TestValues.appDescriptor;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationOnlyDescriptorProviderTest {

  private static final String APP_FOO_ID = "app-foo-1.0.0";
  private static final String APP_BAR_ID = "app-bar-1.0.0";
  private static final String APP_BAZ_ID = "app-baz-1.0.0";

  @InjectMocks private ApplicationOnlyDescriptorProvider provider;
  @Mock private ApplicationManagerService applicationManagerService;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(this);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("descriptorsForRequestDataProvider")
  void getDescriptorsForRequest_positive(@SuppressWarnings("unused") String testName,
    List<String> requestAppIds, List<ApplicationDescriptor> expectedDescriptors) {
    var entitlementRequest = entitlementRequest(requestAppIds);
    when(applicationManagerService.getApplicationDescriptors(requestAppIds, OKAPI_TOKEN))
      .thenReturn(expectedDescriptors);

    var actual = provider.getDescriptors(entitlementRequest);

    assertThat(actual).containsExactlyInAnyOrderElementsOf(expectedDescriptors);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("emptyRequestDataProvider")
  void getDescriptorsForRequest_positive_emptyApplications(@SuppressWarnings("unused") String testName,
    List<String> requestAppIds) {
    var entitlementRequest = entitlementRequest(requestAppIds);

    var actual = provider.getDescriptors(entitlementRequest);

    assertThat(actual).isEmpty();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("descriptorsForContextDataProvider")
  void getDescriptorsForContext_positive(@SuppressWarnings("unused") String testName,
    List<ApplicationDescriptor> contextDescriptors) {
    var actual = provider.getDescriptors(stageContext(contextDescriptors));

    assertThat(actual).containsExactlyInAnyOrderElementsOf(contextDescriptors);
  }

  private static Stream<Arguments> descriptorsForRequestDataProvider() {
    return Stream.of(
      arguments("single application",
        List.of(APP_FOO_ID),
        List.of(appDescriptor(APP_FOO_ID))
      ),
      arguments("multiple applications",
        List.of(APP_FOO_ID, APP_BAR_ID, APP_BAZ_ID),
        List.of(
          appDescriptor(APP_FOO_ID),
          appDescriptor(APP_BAR_ID),
          appDescriptor(APP_BAZ_ID)
        )
      ),
      arguments("two applications",
        List.of(APP_FOO_ID, APP_BAR_ID),
        List.of(
          appDescriptor(APP_FOO_ID),
          appDescriptor(APP_BAR_ID)
        )
      )
    );
  }

  private static Stream<Arguments> emptyRequestDataProvider() {
    return Stream.of(
      arguments("empty application list", emptyList())
    );
  }

  private static Stream<Arguments> descriptorsForContextDataProvider() {
    return Stream.of(
      arguments("empty context descriptors", emptyList()),
      arguments("single descriptor taken from context",
        List.of(appDescriptor(APP_FOO_ID))
      ),
      arguments("multiple descriptors taken from context",
        List.of(
          appDescriptor(APP_FOO_ID),
          appDescriptor(APP_BAR_ID),
          appDescriptor(APP_BAZ_ID)
        )
      )
    );
  }

  private static CommonStageContext stageContext(List<ApplicationDescriptor> contextDescriptors) {
    var contextParams = Map.of(PARAM_APP_DESCRIPTORS, contextDescriptors);
    return commonStageContext(FLOW_ID, emptyMap(), contextParams);
  }

  private static EntitlementRequest entitlementRequest(List<String> applicationIds) {
    return EntitlementRequest.builder()
      .type(ENTITLE)
      .tenantId(TENANT_ID)
      .applications(applicationIds)
      .okapiToken(OKAPI_TOKEN)
      .build();
  }
}
