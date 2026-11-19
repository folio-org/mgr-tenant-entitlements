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

/**
 * Records every failed attempt that is going to be retried.
 *
 * <p>Listens to {@link MethodRetryEvent}s published by the Spring retry interceptors, logs the failure and stores its
 * stack trace in the current module stage context, so the retry history is visible in the flow stage details.
 * Targets that are not annotated with {@link FolioModuleCallsRetryable} or {@link KeycloakCallsRetryable} are
 * ignored.</p>
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class RetryAttemptListener {

  private final ThreadLocalModuleStageContext threadLocalModuleStageContext;

  /**
   * Logs the failed attempt and adds it to the retry information of the current stage context.
   *
   * <p>Aborted events (the failure is not retried: non-retryable exception or exhausted attempts) are ignored.</p>
   *
   * @param event retry event published after a failed attempt
   */
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

  /**
   * Resolves a human-readable name of the retried operation from the annotation on the target class.
   *
   * @param event retry event
   * @return {@code "Folio Module call"}, {@code "Keycloak access"}, or {@code null} if the target is not annotated
   */
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
