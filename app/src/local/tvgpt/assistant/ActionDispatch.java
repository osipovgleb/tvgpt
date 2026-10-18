package local.tvgpt.assistant;

import java.util.concurrent.*;
import java.util.function.Consumer;

/** A queued mutation is withdrawn if its owner stops waiting before dispatch. */
final class ActionDispatch {
  static <T> T onQueue(
      Consumer<Runnable> queue, Callable<T> action, CancellationToken token, long timeoutMs)
      throws Exception {
    token.check();
    FutureTask<T> task =
        new FutureTask<>(
            () -> {
              token.check();
              return action.call();
            });
    try (CancellationToken.Registration registration = token.onCancel(() -> task.cancel(false))) {
      token.check();
      queue.accept(task);
      try {
        return task.get(timeoutMs, TimeUnit.MILLISECONDS);
      } catch (InterruptedException cancelled) {
        Thread.currentThread().interrupt();
        throw cancelled;
      } catch (ExecutionException failure) {
        Throwable cause = failure.getCause();
        if (cause instanceof Exception) throw (Exception) cause;
        if (cause instanceof Error) throw (Error) cause;
        throw failure;
      }
    } finally {
      task.cancel(false);
    }
  }
}
