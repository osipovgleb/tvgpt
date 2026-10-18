package local.tvgpt.assistant;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class CancellationTokenTest {
  public static void main(String[] args) throws Exception {
    CancellationToken token = new CancellationToken();
    AtomicInteger calls = new AtomicInteger();
    token.onCancel(calls::incrementAndGet);
    CancellationToken.Registration removed = token.onCancel(() -> calls.addAndGet(100));
    removed.close();
    if (!token.cancel() || token.cancel() || calls.get() != 1)
      throw new AssertionError("Cancel exactly once");
    token.onCancel(calls::incrementAndGet);
    if (calls.get() != 2) throw new AssertionError("Late registration not notified");
    try {
      token.check();
      throw new AssertionError("Cancelled check accepted");
    } catch (CancellationException expected) {
    }
    if (CancellationToken.NONE.cancel() || CancellationToken.NONE.cancelled())
      throw new AssertionError("NONE must be immutable");
    CancellationToken safe = new CancellationToken();
    safe.onCancel(
        () -> {
          throw new IllegalStateException("listener");
        });
    safe.onCancel(calls::incrementAndGet);
    safe.cancel();
    if (calls.get() != 3) throw new AssertionError("Failed callback blocked following cleanup");
    Thread.currentThread().interrupt();
    try {
      CancellationToken.NONE.check();
      throw new AssertionError("Ignored interruption");
    } catch (CancellationException expected) {
    } finally {
      Thread.interrupted();
    }
    System.out.println(
        "CancellationTokenTest: immutable none, once-only callbacks, unregister and interruption"
            + " passed");
  }
}
