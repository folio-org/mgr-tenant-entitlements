package org.folio.entitlement.retry;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.folio.entitlement.utils.FlowUtils.addErrorInformation;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.retry.annotations.FolioModuleCallsRetryable;
import org.folio.entitlement.retry.annotations.KeycloakCallsRetryable;
import org.folio.entitlement.service.stage.ThreadLocalModuleStageContext;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.resilience.retry.MethodRetryEvent;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class RetryAttemptListener {

  private final ThreadLocalModuleStageContext threadLocalModuleStageContext;

  @EventListener
  public void onRetryEvent(MethodRetryEvent event) {
    if (event.isRetryAborted()) {
      return;
    }

    var description = describeOperation(event);
    if (description == null) {
      return;
    }

    var failure = event.getFailure();
    log.error(String.format("Error occurred for %s - retrying", description), failure);
    addErrorInformation(getStackTrace(failure), threadLocalModuleStageContext);
  }

  private static String describeOperation(MethodRetryEvent event) {
    var target = event.getSource().getThis();
    var targetClass = target != null ? AopUtils.getTargetClass(target) : event.getMethod().getDeclaringClass();

    if (AnnotatedElementUtils.hasAnnotation(targetClass, FolioModuleCallsRetryable.class)) {
      return "Folio Module call";
    }

    if (AnnotatedElementUtils.hasAnnotation(targetClass, KeycloakCallsRetryable.class)) {
      return "Keycloak access";
    }

    return null;
  }
}
