package local.tvgpt.assistant;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class AuthVoiceLifecycleTest {
  public static void main(String[] args) throws Exception {
    RefreshFlight<String> gate = new RefreshFlight<>();
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    ExecutorService pool = Executors.newFixedThreadPool(2);
    Callable<String> operation =
        () -> {
          calls.incrementAndGet();
          entered.countDown();
          release.await();
          return "new token";
        };
    Future<String> a = pool.submit(() -> gate.run(7, operation));
    entered.await();
    Future<String> b = pool.submit(() -> gate.run(7, operation));
    release.countDown();
    if (!a.get().equals("new token") || !b.get().equals("new token") || calls.get() != 1)
      throw new AssertionError("same account revision must share one refresh");
    if (!gate.run(8, () -> "new login").equals("new login")) throw new AssertionError();
    pool.shutdownNow();
    RefreshFlight<String> failed = new RefreshFlight<>();
    try {
      failed.run(
          1,
          () -> {
            throw new Exception("temporary network outage");
          });
      throw new AssertionError();
    } catch (Exception expected) {
      if (!"temporary network outage".equals(expected.getMessage())) throw expected;
    }
    if (!failed.run(1, () -> "recovered").equals("recovered"))
      throw new AssertionError("failed refresh cannot poison the account permanently");

    WakeRecovery wake = new WakeRecovery();
    long first = wake.start();
    if (wake.retryDelay(first) != 500
        || wake.retryDelay(first) != 1500
        || wake.retryDelay(first) != 4000
        || wake.retryDelay(first) != -1) throw new AssertionError("recovery must be bounded");
    wake.stop();
    if (wake.consume(first)) throw new AssertionError("stopped recorder cannot deliver wake");
    if (wake.active(first) || wake.retryDelay(first) != -1)
      throw new AssertionError("stop must invalidate retries");
    long second = wake.start();
    if (wake.active(first) || !wake.active(second))
      throw new AssertionError("old recorder cannot revive after restart");
    if (wake.consume(first)) throw new AssertionError("old recorder cannot stop new capture");
    if (!wake.active(second)) throw new AssertionError();
    wake.close();
    if (wake.active(second) || wake.start() != -1)
      throw new AssertionError("disposed capture cannot restart");
    System.out.println("AuthVoiceLifecycleTest passed");
  }
}
