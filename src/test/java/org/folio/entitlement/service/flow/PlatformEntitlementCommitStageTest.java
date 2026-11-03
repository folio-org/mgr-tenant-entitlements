package org.folio.entitlement.service.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.UPGRADE;
import static org.folio.entitlement.service.flow.PlatformTestValues.platformContext;
import static org.folio.entitlement.service.flow.PlatformTestValues.platformRequest;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
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

  @InjectMocks private PlatformEntitlementCommitStage stage;
  @Mock private PlatformEntitlementRepository repository;

  @Test
  void execute_positive_entitlesWhenNothingIsEntitled() {
    when(repository.findById("app-platform")).thenReturn(Optional.empty());

    stage.execute(platformContext(platformRequest(APP_V1, ENTITLE, OKAPI_TOKEN)));

    var captor = ArgumentCaptor.forClass(PlatformEntitlementEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getApplicationId()).isEqualTo(APP_V1);
    verifyNoMoreInteractions(repository);
  }

  @Test
  void execute_positive_upgradeReplacesEntitledVersion() {
    when(repository.findById("app-platform")).thenReturn(Optional.of(PlatformEntitlementEntity.of(APP_V1)));

    stage.execute(platformContext(platformRequest(APP_V2, UPGRADE, OKAPI_TOKEN)));

    var captor = ArgumentCaptor.forClass(PlatformEntitlementEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getApplicationId()).isEqualTo(APP_V2);
    verifyNoMoreInteractions(repository);
  }

  @Test
  void execute_positive_alreadyEntitledVersionWritesNothing() {
    when(repository.findById("app-platform")).thenReturn(Optional.of(PlatformEntitlementEntity.of(APP_V1)));

    stage.execute(platformContext(platformRequest(APP_V1, ENTITLE, OKAPI_TOKEN)));

    verifyNoMoreInteractions(repository);
  }
}
