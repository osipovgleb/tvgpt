package local.tvgpt.assistant.data;

import java.util.concurrent.*;

public final class AppDataWork {
  private static final ExecutorService IO =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "tvgpt-archive"));

  private AppDataWork() {}

  public static void submit(Runnable work) {
    IO.submit(
        () -> {
          try {
            work.run();
          } catch (RuntimeException error) {
            android.util.Log.w(
                "TVGPTData", "Archive task failed: " + error.getClass().getSimpleName());
          }
        });
  }
}
