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
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

@Log4j2
@UtilityClass
public class ParallelUtil {

  public static <T> List<T> runParallel(Supplier<ExecutorService> executorSupplier, Collection<Callable<T>> tasks,
    Consumer<Throwable> errorHandler) {
    var executor = executorSupplier.get();
    List<Future<T>> futures = null; // Executes all tasks in parallel
    try {
      futures = executor.invokeAll(tasks);
      List<T> results = new ArrayList<>();

      for (Future<T> future : futures) {
        execute(errorHandler, future, results);
      }

      executor.shutdown();
      if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
        log.warn("Executor didn't shutdown on time");
      }
      return results;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InterruptException("Execution interrupter", e);
    }
  }

  private static <T> void execute(Consumer<Throwable> errorHandler, Future<T> future, List<T> results)
    throws InterruptedException {
    try {
      results.add(future.get());
    } catch (ExecutionException e) {
      if (errorHandler != null) {
        errorHandler.accept(e.getCause());
      }
    }
  }

  public static class InterruptException extends RuntimeException {
    public InterruptException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
