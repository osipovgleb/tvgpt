package local.tvgpt.assistant;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;

/** One operation's cancellation; callbacks never run under its state lock. */
public final class CancellationToken {
  public interface Registration extends AutoCloseable {
    @Override
    void close();
  }

  public static final CancellationToken NONE = new CancellationToken(false);
  private final boolean cancellable;
  private final Set<Runnable> callbacks = new LinkedHashSet<>();
  private volatile boolean cancelled;

  public CancellationToken() {
    this(true);
  }

  private CancellationToken(boolean cancellable) {
    this.cancellable = cancellable;
  }

  public boolean cancelled() {
    return cancelled;
  }

  public void check() {
    if (cancelled || Thread.currentThread().isInterrupted()) throw new CancellationException();
  }

  public Registration onCancel(Runnable callback) {
    if (!cancellable) return () -> {};
    synchronized (this) {
      if (!cancelled) {
        callbacks.add(callback);
        return () -> {
          synchronized (CancellationToken.this) {
            callbacks.remove(callback);
          }
        };
      }
    }
    notifyCallback(callback);
    return () -> {};
  }

  public boolean cancel() {
    ArrayList<Runnable> pending;
    synchronized (this) {
      if (!cancellable || cancelled) return false;
      cancelled = true;
      pending = new ArrayList<>(callbacks);
      callbacks.clear();
    }
    for (Runnable callback : pending) notifyCallback(callback);
    return true;
  }

  private static void notifyCallback(Runnable callback) {
    try {
      callback.run();
    } catch (RuntimeException ignored) {
      /* Cleanup must continue for the remaining resources. */
    }
  }
}
