package local.tvgpt.assistant;

import java.nio.file.*;
import java.util.concurrent.*;

public class SessionConcurrencyTest {
  public static void main(String[] args) throws Exception {
    SessionStore store = new SessionStore(Files.createTempDirectory("tvgpt-concurrency-").toFile());
    SessionStore.State state = store.open(System.currentTimeMillis());
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    Http.reset(0);
    Http.beforePost =
        () -> {
          entered.countDown();
          try {
            release.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        };
    ExecutorService pool = Executors.newFixedThreadPool(2);
    Future<String> request =
        pool.submit(() -> new Agent(new android.content.Context(), state).run("Привет"));
    try {
      if (!entered.await(2, TimeUnit.SECONDS)) throw new AssertionError("Agent did not reach HTTP");
      Future<SessionStore.State> reopen = pool.submit(() -> store.open(System.currentTimeMillis()));
      try {
        SessionStore.State next = reopen.get(1, TimeUnit.SECONDS);
        if (next.id.equals(state.id) || next.history.length() != 0)
          throw new AssertionError("New conversation inherited an in-flight request");
      } catch (TimeoutException e) {
        throw new AssertionError("Reopening blocked behind pending agent HTTP", e);
      }
    } finally {
      release.countDown();
      Http.beforePost = null;
      pool.shutdown();
      pool.awaitTermination(3, TimeUnit.SECONDS);
    }
    request.get();
    System.out.println("SessionConcurrencyTest: reopening stays available during network requests");
  }
}
