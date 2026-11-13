package org.folio.entitlement.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.domain.model.ModuleStageContext.ATTR_RETRY_INFO;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_DESCRIPTOR;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.aopalliance.intercept.MethodInvocation;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.domain.model.RetryInformation;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.folio.stage.FolioModuleInstaller;
import org.folio.entitlement.integration.keycloak.KeycloakModuleResourceCreator;
import org.folio.entitlement.service.stage.ThreadLocalModuleStageContext;
import org.folio.flow.impl.StageContextImpl;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.resilience.retry.MethodRetryEvent;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RetryAttemptListenerTest {

  private static final String ERROR_MESSAGE = "boom";

  @Mock private MethodInvocation invocation;

  @AfterEach
  void tearDown() {
    new ThreadLocalModuleStageContext().clear();
    verifyNoMoreInteractions(invocation);
  }

  @Test
  void onRetryEvent_positive_folioModuleAttemptIsRecorded() {
    var stageContext = moduleStageContext();
    var listener = new RetryAttemptListener(threadLocalContext(stageContext));
    when(invocation.getThis()).thenReturn(new FolioModuleInstaller(null));

    listener.onRetryEvent(new MethodRetryEvent(invocation, failure(), false));

    var retryInformation = retryInformation(stageContext);
    assertThat(retryInformation.getRetriesCount()).isEqualTo(1);
    assertThat(retryInformation.getErrors()).singleElement().asString().contains(ERROR_MESSAGE);
  }

  @Test
  void onRetryEvent_positive_keycloakAttemptIsRecorded() {
    var stageContext = moduleStageContext();
    var listener = new RetryAttemptListener(threadLocalContext(stageContext));
    when(invocation.getThis()).thenReturn(new KeycloakModuleResourceCreator(null, null));

    listener.onRetryEvent(new MethodRetryEvent(invocation, failure(), false));

    var retryInformation = retryInformation(stageContext);
    assertThat(retryInformation.getRetriesCount()).isEqualTo(1);
  }

  @Test
  void onRetryEvent_positive_abortedEventIsIgnored() {
    var stageContext = moduleStageContext();
    var listener = new RetryAttemptListener(threadLocalContext(stageContext));

    listener.onRetryEvent(new MethodRetryEvent(invocation, failure(), true));

    assertThat(retryInformation(stageContext)).isNull();
  }

  @Test
  void onRetryEvent_positive_unannotatedTargetIsIgnored() {
    var stageContext = moduleStageContext();
    var listener = new RetryAttemptListener(threadLocalContext(stageContext));
    when(invocation.getThis()).thenReturn(new Object());

    listener.onRetryEvent(new MethodRetryEvent(invocation, failure(), false));

    assertThat(retryInformation(stageContext)).isNull();
  }

  @Test
  void onRetryEvent_positive_missingStageContextDoesNotFail() {
    var listener = new RetryAttemptListener(new ThreadLocalModuleStageContext());
    when(invocation.getThis()).thenReturn(new FolioModuleInstaller(null));
    var event = new MethodRetryEvent(invocation, failure(), false);

    assertThatCode(() -> listener.onRetryEvent(event)).doesNotThrowAnyException();
  }

  private static RetryInformation retryInformation(ModuleStageContext stageContext) {
    return (RetryInformation) stageContext.get(ATTR_RETRY_INFO);
  }

  private static ThreadLocalModuleStageContext threadLocalContext(ModuleStageContext stageContext) {
    var threadLocalContext = new ThreadLocalModuleStageContext();
    threadLocalContext.set(stageContext);
    return threadLocalContext;
  }

  private static ModuleStageContext moduleStageContext() {
    return new ModuleStageContext(new StageContextImpl("test",
      Map.of(PARAM_REQUEST, EntitlementRequest.builder().build(), PARAM_MODULE_DESCRIPTOR, new ModuleDescriptor()),
      Map.of()));
  }

  private static IntegrationException failure() {
    return new IntegrationException(ERROR_MESSAGE, List.of(), 500);
  }
}
