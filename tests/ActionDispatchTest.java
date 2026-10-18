package local.tvgpt.assistant;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class ActionDispatchTest {
  public static void main(String[] args) throws Exception {
    AtomicReference<Runnable> queued = new AtomicReference<>();
    int[] calls = {0};
    try {
      ActionDispatch.onQueue(queued::set, () -> ++calls[0], CancellationToken.NONE, 1);
      throw new AssertionError("blocked queue did not time out");
    } catch (TimeoutException expected) {
    }
    queued.get().run();
    if (calls[0] != 0) throw new AssertionError("timed-out mutation ran late");
    CancellationToken token = new CancellationToken();
    CountDownLatch posted = new CountDownLatch(1);
    AtomicReference<Throwable> workerFailure = new AtomicReference<>();
    Thread worker =
        new Thread(
            () -> {
              try {
                ActionDispatch.onQueue(
                    task -> {
                      queued.set(task);
                      posted.countDown();
                    },
                    () -> ++calls[0],
                    token,
                    10000);
                throw new AssertionError("cancelled queue returned normally");
              } catch (CancellationException expected) {
              } catch (Throwable failure) {
                workerFailure.set(failure);
              }
            });
    worker.start();
    posted.await();
    token.cancel();
    worker.join(1000);
    if (worker.isAlive()) throw new AssertionError("cancellation did not wake queue waiter");
    if (workerFailure.get() != null) throw new AssertionError(workerFailure.get());
    queued.get().run();
    if (calls[0] != 0) throw new AssertionError("cancelled mutation ran late");
    queued.set(null);
    CancellationToken already = new CancellationToken();
    already.cancel();
    try {
      ActionDispatch.onQueue(queued::set, () -> ++calls[0], already, 100);
      throw new AssertionError("pre-cancelled operation accepted");
    } catch (CancellationException expected) {
    }
    if (queued.get() != null) throw new AssertionError("cancelled operation posted");
    if (ActionDispatch.onQueue(Runnable::run, () -> 42, CancellationToken.NONE, 100) != 42)
      throw new AssertionError("ordinary operation lost");
    System.out.println("ActionDispatchTest passed");
  }
}
