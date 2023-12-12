package org.folio.entitlement.support;

import java.util.function.Consumer;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

public class MockTransactionOperations implements TransactionOperations {

  @Override
  public <T> T execute(TransactionCallback<T> action) throws TransactionException {
    return action.doInTransaction(new SimpleTransactionStatus(false));
  }

  @Override
  public void executeWithoutResult(Consumer<TransactionStatus> action) throws TransactionException {
    action.accept(new SimpleTransactionStatus(false));
  }
}
