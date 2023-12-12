package org.folio.entitlement.service;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

/**
 * Factory for creating new fork-join worker threads with inherited ClassLoader. Before Java 9 ForkJoinPool.common()
 * returns an Executor with a ClassLoader of a main Thread, in Java 9 this behaviour changes, and return an executor
 * with the system jdk system classloader.
 */
public class InheritedClassLoaderThreadFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {

  @Override
  public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
    return new InheritedClassLoaderThread(pool);
  }

  private static final class InheritedClassLoaderThread extends ForkJoinWorkerThread {

    private InheritedClassLoaderThread(final ForkJoinPool pool) {
      super(pool);
      setContextClassLoader(Thread.currentThread().getContextClassLoader());
    }
  }
}
