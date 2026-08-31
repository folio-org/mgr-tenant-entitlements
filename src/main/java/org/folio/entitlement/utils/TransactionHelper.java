package org.folio.entitlement.utils;

import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

import java.util.function.Consumer;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Utility component that safely bridges a transactional operation into the post-commit window.
 *
 * <p>Solves the problem that a {@code @Transactional(REQUIRED)} method called inside an {@code afterCommit} hook
 * does not open a new physical transaction — Spring participates in the synchronization context left by the outer
 * commit but no real DB transaction is active, so Hibernate rejects {@code @Modifying} queries with
 * {@code TransactionRequiredException}. By wrapping the callback in a {@code PROPAGATION_REQUIRES_NEW}
 * {@link TransactionTemplate}, this component ensures that every post-commit action always runs inside a fresh
 * physical transaction.</p>
 */
@Log4j2
@Component
public class TransactionHelper {

  private final TransactionTemplate transactionTemplate;

  public TransactionHelper(PlatformTransactionManager transactionManager) {
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
  }

  /**
   * Schedules {@code action} to run in a new transaction after the current transaction commits.
   *
   * <p>If transaction synchronization is active, registers an after-commit hook that executes the action inside a
   * {@code REQUIRES_NEW} transaction. If synchronization is not active, the action is ignored and a warning is
   * logged — this typically indicates the method was called outside of any transactional context.</p>
   *
   * @param action the work to execute in a new transaction after commit; receives the active {@link TransactionStatus}
   */
  public void executeAfterCommitInNewTrx(Consumer<TransactionStatus> action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          transactionTemplate.executeWithoutResult(action);
        }
      });
    } else {
      log.warn("Transaction synchronization is not active. Action is ignored.");
    }
  }
}
