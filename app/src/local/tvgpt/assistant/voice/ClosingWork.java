package local.tvgpt.assistant.voice;

import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

public final class ClosingWork {
  private ClosingWork() {}

  public static void submit(
      Executor executor, BooleanSupplier closed, Runnable task, boolean duringClose) {
    executor.execute(
        () -> {
          if (duringClose || !closed.getAsBoolean()) task.run();
        });
  }
}
