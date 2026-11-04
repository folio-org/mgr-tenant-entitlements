package org.folio.entitlement.service.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.UPGRADE;
import static org.folio.entitlement.service.flow.PlatformTestValues.platformContext;
import static org.folio.entitlement.service.flow.PlatformTestValues.platformRequest;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PlatformEntitlementCommitStageTest {

  private static final String APP_V1 = "app-platform-1.0.0";
  private static final String APP_V2 = "app-platform-2.0.0";
  private static final String OTHER_APP_V1 = "other-platform-1.0.0";

  @InjectMocks private PlatformEntitlementCommitStage stage;
  @Mock private PlatformEntitlementRepository repository;

  @Test
  void execute_positive_savesEntitledVersion() {
    stage.execute(platformContext(platformRequest(ENTITLE, OKAPI_TOKEN, APP_V1)));

    assertThat(savedApplicationIds()).containsExactly(APP_V1);
  }

  @Test
  void execute_positive_upgradeSavesRequestedVersion() {
    stage.execute(platformContext(platformRequest(UPGRADE, OKAPI_TOKEN, APP_V2)));

    assertThat(savedApplicationIds()).containsExactly(APP_V2);
  }

  @Test
  void execute_positive_retryOfEntitledVersionSavesSameState() {
    stage.execute(platformContext(platformRequest(ENTITLE, OKAPI_TOKEN, APP_V1)));

    assertThat(savedApplicationIds()).containsExactly(APP_V1);
  }

  @Test
  void execute_positive_savesAllApplicationsInOneCall() {
    stage.execute(platformContext(platformRequest(ENTITLE, OKAPI_TOKEN, APP_V1, OTHER_APP_V1)));

    assertThat(savedApplicationIds()).containsExactly(APP_V1, OTHER_APP_V1);
  }

  @SuppressWarnings("unchecked")
  private List<String> savedApplicationIds() {
    var captor = ArgumentCaptor.forClass(List.class);
    verify(repository).saveAll(captor.capture());
    verifyNoMoreInteractions(repository);
    return ((List<PlatformEntitlementEntity>) captor.getValue()).stream()
      .map(PlatformEntitlementEntity::getApplicationId)
      .toList();
  }
}
