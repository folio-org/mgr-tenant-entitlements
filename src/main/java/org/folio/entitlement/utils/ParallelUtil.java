package org.folio.entitlement.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ParallelUtil {

  public static <T> List<T> runParallel(Supplier<ExecutorService> executorSupplier, Collection<Callable<T>> tasks,
    Consumer<Throwable> errorHandler) {
    var executor = executorSupplier.get();
    List<Future<T>> futures = null; // Executes all tasks in parallel
    try {
      futures = executor.invokeAll(tasks);
      List<T> results = new ArrayList<>();

      for (Future<T> future : futures) {
        try {
          results.add(future.get());
        } catch (ExecutionException e) {
          if (errorHandler != null) {
            errorHandler.accept(e.getCause());
          }
        }
      }

      executor.shutdown();
      if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
        log.warn("Executor didn't shutdown on time");
      }
      return results;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Execution interrupter", e);
    }
  }
}
