package org.folio.entitlement.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ParallelUtilTest {

  @Mock private ExecutorService mockExecutor;
  @Mock private Callable<String> mockTask1;
  @Mock private Callable<String> mockTask2;
  @Mock private Consumer<Throwable> mockErrorHandler;

  @Test
  void testRunParallel_successfulExecution() throws Exception {
    var mockFuture1 = mock(Future.class);
    var mockFuture2 = mock(Future.class);

    when(mockExecutor.invokeAll(any(Collection.class))).thenReturn(List.of(mockFuture1, mockFuture2));
    when(mockFuture1.get()).thenReturn("Task 1 Result");
    when(mockFuture2.get()).thenReturn("Task 2 Result");

    var tasks = List.of(mockTask1, mockTask2);
    var results = ParallelUtil.runParallel(() -> mockExecutor, tasks, mockErrorHandler);

    assertThat(results).hasSize(2);
    assertThat(results.get(0)).isEqualTo("Task 1 Result");
    assertThat(results.get(1)).isEqualTo("Task 2 Result");

    verify(mockExecutor, times(1)).shutdown();
    verify(mockExecutor, times(1)).awaitTermination(1, TimeUnit.MINUTES);
  }

  @Test
  void testRunParallel_taskThrowsExecutionException() throws Exception {
    var mockFuture1 = mock(Future.class);
    var mockFuture2 = mock(Future.class);

    when(mockExecutor.invokeAll(any(Collection.class))).thenReturn(List.of(mockFuture1, mockFuture2));
    when(mockFuture1.get()).thenThrow(new ExecutionException(new RuntimeException("Task failed")));
    when(mockFuture2.get()).thenReturn("Task 2 Result");

    var tasks = List.of(mockTask1, mockTask2);
    var results = ParallelUtil.runParallel(() -> mockExecutor, tasks, mockErrorHandler);

    assertThat(results).hasSize(1);
    assertThat(results.get(0)).isEqualTo("Task 2 Result");

    verify(mockErrorHandler, times(1)).accept(any(Throwable.class));
    verify(mockExecutor, times(1)).shutdown();
    verify(mockExecutor, times(1)).awaitTermination(1, TimeUnit.MINUTES);
  }

  @Test
  void testRunParallel_interruptedExecution() throws Exception {
    var mockFuture1 = mock(Future.class);
    var mockFuture2 = mock(Future.class);

    when(mockExecutor.invokeAll(any(Collection.class))).thenReturn(List.of(mockFuture1, mockFuture2));
    var interruptException = new InterruptedException("Execution interrupted");
    when(mockFuture1.get()).thenThrow(interruptException);

    var tasks = List.of(mockTask1, mockTask2);

    assertThatThrownBy(() -> {
      ParallelUtil.runParallel(() -> mockExecutor, tasks, mockErrorHandler);
    }).isInstanceOf(RuntimeException.class).hasMessage("Execution interrupter").hasCause(interruptException);
  }
}
