package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.PLATFORM_UPGRADE;
import static org.folio.entitlement.service.flow.PlatformTestValues.platformContext;
import static org.folio.entitlement.service.flow.PlatformTestValues.platformRequest;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestValues.appDescriptor;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PlatformEntitledDescriptorLoaderTest {

  private static final String APP_V1 = "app-platform-1.0.0";
  private static final String APP_V2 = "app-platform-2.0.0";
  private static final String OTHER_V1 = "other-platform-1.0.0";
  private static final String OTHER_V2 = "other-platform-2.0.0";

  @InjectMocks private PlatformEntitledDescriptorLoader loader;
  @Mock private PlatformEntitlementRepository repository;
  @Mock private ApplicationManagerService applicationManagerService;

  @Test
  void execute_positive_entitledDescriptorIsLoaded() {
    when(repository.findAllById(List.of("app-platform"))).thenReturn(List.of(PlatformEntitlementEntity.of(APP_V1)));
    when(applicationManagerService.getApplicationDescriptors(List.of(APP_V1), OKAPI_TOKEN))
      .thenReturn(List.of(appDescriptor(APP_V1)));
    var context = platformContext(platformRequest(PLATFORM_UPGRADE, OKAPI_TOKEN, APP_V2));

    loader.execute(context);

    assertThat(context.getEntitledApplicationDescriptors()).containsExactly(appDescriptor(APP_V1));
  }

  @Test
  void execute_positive_sameVersionRetryLoadsTheEntitledDescriptor() {
    when(repository.findAllById(List.of("app-platform"))).thenReturn(List.of(PlatformEntitlementEntity.of(APP_V2)));
    when(applicationManagerService.getApplicationDescriptors(List.of(APP_V2), OKAPI_TOKEN))
      .thenReturn(List.of(appDescriptor(APP_V2)));
    var context = platformContext(platformRequest(PLATFORM_UPGRADE, OKAPI_TOKEN, APP_V2));

    loader.execute(context);

    assertThat(context.getEntitledApplicationDescriptors()).containsExactly(appDescriptor(APP_V2));
  }

  @Test
  void execute_positive_everyRequestedApplicationHasItsEntitledDescriptorLoaded() {
    when(repository.findAllById(List.of("app-platform", "other-platform")))
      .thenReturn(List.of(PlatformEntitlementEntity.of(APP_V1), PlatformEntitlementEntity.of(OTHER_V1)));
    when(applicationManagerService.getApplicationDescriptors(List.of(APP_V1, OTHER_V1), OKAPI_TOKEN))
      .thenReturn(List.of(appDescriptor(APP_V1), appDescriptor(OTHER_V1)));
    var context = platformContext(platformRequest(PLATFORM_UPGRADE, OKAPI_TOKEN, APP_V2, OTHER_V2));

    loader.execute(context);

    assertThat(context.getEntitledApplicationDescriptors())
      .containsExactlyInAnyOrder(appDescriptor(APP_V1), appDescriptor(OTHER_V1));
  }
}
