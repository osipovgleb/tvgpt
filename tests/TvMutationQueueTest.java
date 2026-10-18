package local.tvgpt.assistant;

import android.content.Context;
import android.os.Handler;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

public final class TvMutationQueueTest {
  private static void run(boolean cancelToken) throws Exception {
    Context context = new Context();
    CancellationToken token = new CancellationToken();
    TvTools tools = cancelToken ? new TvTools(context, token) : new TvTools(context);
    Method raw = TvTools.class.getDeclaredMethod("executeRaw", String.class, JSONObject.class);
    raw.setAccessible(true);
    AtomicReference<Throwable> result = new AtomicReference<>();
    Handler.blocked = true;
    Handler.posted = new CountDownLatch(1);
    Thread worker =
        new Thread(
            () -> {
              try {
                raw.invoke(tools, "volume", new JSONObject().put("muted", true));
              } catch (InvocationTargetException failure) {
                result.set(failure.getCause());
              } catch (Throwable failure) {
                result.set(failure);
              }
            });
    worker.start();
    if (!Handler.posted.await(1, TimeUnit.SECONDS)) throw new AssertionError("mutation not queued");
    if (cancelToken) token.cancel();
    else worker.interrupt();
    worker.join(1000);
    if (worker.isAlive()) throw new AssertionError("cancel did not wake mutation worker");
    if (!(result.get() instanceof InterruptedException)
        && !(result.get() instanceof CancellationException))
      throw new AssertionError("cancellation not propagated", result.get());
    Handler.pending.run();
    Handler.blocked = false;
    if (context.audio.mutations != 0)
      throw new AssertionError("abandoned main mutation executed late");
  }

  public static void main(String[] args) throws Exception {
    run(false);
    run(true);
    System.out.println("TvMutationQueueTest passed");
  }
}
