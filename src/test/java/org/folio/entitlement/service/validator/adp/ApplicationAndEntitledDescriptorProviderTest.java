package org.folio.entitlement.service.validator.adp;

import static java.util.Collections.emptyList;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_APP_DESCRIPTORS;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.appDescriptor;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.entitlement.support.TestValues;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationAndEntitledDescriptorProviderTest {

  private static final String APP_FOO_ID = "app-foo-1.0.0";
  private static final String APP_FOO_UPDATE_ID = "app-foo-1.1.0";
  private static final String APP_BAR_ID = "app-bar-1.0.0";
  private static final String APP_BAR_UPDATE_ID = "app-bar-2.0.0";
  private static final String APP_BAZ_ID = "app-baz-1.0.0";
  private static final String APP_BAZ_UPDATE_ID = "app-baz-1.5.0";

  @Mock private ApplicationManagerService applicationManagerService;
  @Mock private EntitlementCrudService entitlementService;
  @InjectMocks
  private ApplicationAndEntitledDescriptorProvider provider;

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("descriptorsForRequestDataProvider")
  void getDescriptorsForRequest_positive(@SuppressWarnings("unused") String testName,
    List<String> requestAppIds, List<Entitlement> entitlements,
    List<String> descriptorIdsToLoad, List<ApplicationDescriptor> expectedDescriptors) {
    when(entitlementService.findByTenantId(TENANT_ID)).thenReturn(entitlements);
    if (isNotEmpty(descriptorIdsToLoad)) {
      when(applicationManagerService.getApplicationDescriptors(descriptorIdsToLoad, OKAPI_TOKEN))
        .thenReturn(expectedDescriptors);
    }

    var actual = provider.getDescriptors(entitlementRequest(requestAppIds));

    assertThat(actual).containsExactlyInAnyOrderElementsOf(expectedDescriptors);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("descriptorsForContextDataProvider")
  void getDescriptorsForContext_positive(@SuppressWarnings("unused") String testName,
    List<ApplicationDescriptor> contextDescriptors, List<Entitlement> entitlements,
    List<String> descriptorIdsToLoad, List<ApplicationDescriptor> expectedDescriptors) {
    when(entitlementService.findByTenantId(TENANT_ID)).thenReturn(entitlements);
    if (isNotEmpty(descriptorIdsToLoad)) {
      when(applicationManagerService.getApplicationDescriptors(descriptorIdsToLoad, OKAPI_TOKEN))
        .thenReturn(mapItems(descriptorIdsToLoad, TestValues::appDescriptor));
    }

    var actual = provider.getDescriptors(stageContext(contextDescriptors));

    assertThat(actual).containsExactlyInAnyOrderElementsOf(expectedDescriptors);
  }

  private static Stream<Arguments> descriptorsForRequestDataProvider() {
    return Stream.of(
      arguments("no applications are requested and no entitlements",
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList()
      ),
      arguments("nothing is entitled",
        List.of(APP_FOO_ID, APP_BAR_ID, APP_BAZ_ID),
        emptyList(),
        List.of(APP_FOO_ID, APP_BAR_ID, APP_BAZ_ID),
        List.of(
          appDescriptor(APP_FOO_ID),
          appDescriptor(APP_BAR_ID),
          appDescriptor(APP_BAZ_ID)
        )
      ),
      arguments("one application is entitled",
        List.of(APP_BAR_ID, APP_BAZ_ID),
        List.of(entitlement(APP_FOO_ID)),
        List.of(APP_BAR_ID, APP_BAZ_ID, APP_FOO_ID),
        List.of(
          appDescriptor(APP_FOO_ID),
          appDescriptor(APP_BAR_ID),
          appDescriptor(APP_BAZ_ID)
        )
      ),
      arguments("multiple applications are entitled",
        List.of(APP_BAZ_ID),
        List.of(entitlement(APP_FOO_ID), entitlement(APP_BAR_ID)),
        List.of(APP_BAZ_ID, APP_FOO_ID, APP_BAR_ID),
        List.of(
          appDescriptor(APP_FOO_ID),
          appDescriptor(APP_BAR_ID),
          appDescriptor(APP_BAZ_ID)
        )
      ),
      arguments("previous version of an application is entitled",
        List.of(APP_FOO_UPDATE_ID, APP_BAR_ID, APP_BAZ_ID),
        List.of(entitlement(APP_FOO_ID)),
        List.of(APP_FOO_UPDATE_ID, APP_BAR_ID, APP_BAZ_ID),
        List.of(
          appDescriptor(APP_FOO_UPDATE_ID),
          appDescriptor(APP_BAR_ID),
          appDescriptor(APP_BAZ_ID)
        )
      ),
      arguments("all applications are entitled and updates are requested",
        List.of(APP_FOO_UPDATE_ID, APP_BAR_UPDATE_ID, APP_BAZ_UPDATE_ID),
        List.of(entitlement(APP_FOO_ID), entitlement(APP_BAR_ID), entitlement(APP_BAZ_ID)),
        List.of(APP_FOO_UPDATE_ID, APP_BAR_UPDATE_ID, APP_BAZ_UPDATE_ID),
        List.of(
          appDescriptor(APP_FOO_UPDATE_ID),
          appDescriptor(APP_BAR_UPDATE_ID),
          appDescriptor(APP_BAZ_UPDATE_ID)
        )
      )
    );
  }

  private static Stream<Arguments> descriptorsForContextDataProvider() {
    return Stream.of(
      arguments("no applications are requested and no entitlements",
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList()
      ),
      arguments("nothing is entitled",
        List.of(
          appDescriptor(APP_FOO_ID),
          appDescriptor(APP_BAR_ID),
          appDescriptor(APP_BAZ_ID)),
        emptyList(),
        emptyList(),
        List.of(
          appDescriptor(APP_FOO_ID),
          appDescriptor(APP_BAR_ID),
          appDescriptor(APP_BAZ_ID)
        )
      ),
      arguments("one application is entitled",
        List.of(
          appDescriptor(APP_BAR_ID),
          appDescriptor(APP_BAZ_ID)),
        List.of(entitlement(APP_FOO_ID)),
        List.of(APP_FOO_ID),
        List.of(
          appDescriptor(APP_BAR_ID),
          appDescriptor(APP_BAZ_ID),
          appDescriptor(APP_FOO_ID)
        )
      ),
      arguments("multiple applications are entitled",
        List.of(appDescriptor(APP_BAZ_ID)),
        List.of(entitlement(APP_FOO_ID), entitlement(APP_BAR_ID)),
        List.of(APP_FOO_ID, APP_BAR_ID),
        List.of(
          appDescriptor(APP_FOO_ID),
          appDescriptor(APP_BAR_ID),
          appDescriptor(APP_BAZ_ID)
        )
      ),
      arguments("previous version of an application is entitled",
        List.of(
          appDescriptor(APP_FOO_UPDATE_ID),
          appDescriptor(APP_BAR_ID),
          appDescriptor(APP_BAZ_ID)),
        List.of(entitlement(APP_FOO_ID)),
        emptyList(),
        List.of(
          appDescriptor(APP_FOO_UPDATE_ID),
          appDescriptor(APP_BAR_ID),
          appDescriptor(APP_BAZ_ID)
        )
      ),
      arguments("all applications are entitled and updates are requested",
        List.of(
          appDescriptor(APP_FOO_UPDATE_ID),
          appDescriptor(APP_BAR_UPDATE_ID),
          appDescriptor(APP_BAZ_UPDATE_ID)),
        List.of(entitlement(APP_FOO_ID), entitlement(APP_BAR_ID), entitlement(APP_BAZ_ID)),
        emptyList(),
        List.of(
          appDescriptor(APP_FOO_UPDATE_ID),
          appDescriptor(APP_BAR_UPDATE_ID),
          appDescriptor(APP_BAZ_UPDATE_ID)
        )
      )
    );
  }

  private static CommonStageContext stageContext(List<ApplicationDescriptor> contextDescriptors) {
    var flowParams = Map.of(PARAM_REQUEST, entitlementRequest(
      mapItems(contextDescriptors, ApplicationDescriptor::getId)));
    var contextParams = Map.of(PARAM_APP_DESCRIPTORS, contextDescriptors);
    return commonStageContext(FLOW_ID, flowParams, contextParams);
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
