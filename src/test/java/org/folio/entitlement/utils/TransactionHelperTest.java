package org.folio.entitlement.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TransactionHelperTest {

  @Mock private PlatformTransactionManager transactionManager;
  @InjectMocks private TransactionHelper transactionHelper;

  @Test
  void executeAfterCommitInNewTrx_positive_synchronizationActive() {
    var txStatus = mock(org.springframework.transaction.TransactionStatus.class);
    when(transactionManager.getTransaction(any())).thenReturn(txStatus);

    var executed = new AtomicBoolean(false);
    TransactionSynchronizationManager.initSynchronization();
    try {
      transactionHelper.executeAfterCommitInNewTrx(status -> executed.set(true));
      TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }

    assertThat(executed.get()).isTrue();
    verify(transactionManager).getTransaction(any());
    verify(transactionManager).commit(txStatus);
  }

  @Test
  void executeAfterCommitInNewTrx_positive_noActiveSynchronization() {
    var executed = new AtomicBoolean(false);
    transactionHelper.executeAfterCommitInNewTrx(status -> executed.set(true));

    assertThat(executed.get()).isFalse();
    verify(transactionManager, never()).getTransaction(any());
  }
}
