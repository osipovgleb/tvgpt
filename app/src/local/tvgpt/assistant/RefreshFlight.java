package local.tvgpt.assistant;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/** Shares a refresh result (including failure) for one immutable account revision. */
final class RefreshFlight<T> {
  private long revision = Long.MIN_VALUE;
  private CompletableFuture<T> flight;

  T run(long expectedRevision, Callable<T> operation) throws Exception {
    CompletableFuture<T> pending;
    boolean owner;
    synchronized (this) {
      owner = flight == null || revision != expectedRevision;
      if (owner) {
        revision = expectedRevision;
        flight = new CompletableFuture<>();
      }
      pending = flight;
    }
    if (owner) {
      try {
        pending.complete(operation.call());
      } catch (Throwable failure) {
        pending.completeExceptionally(failure);
        synchronized (this) {
          if (flight == pending) flight = null;
        }
      }
    }
    try {
      return pending.get();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw interrupted;
    } catch (ExecutionException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof Exception) throw (Exception) cause;
      if (cause instanceof Error) throw (Error) cause;
      throw new IllegalStateException(cause);
    }
  }
}
