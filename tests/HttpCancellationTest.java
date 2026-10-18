package local.tvgpt.assistant;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class HttpCancellationTest {
  private static final AtomicInteger opened = new AtomicInteger();
  private static volatile FakeConnection latest;
  private static volatile boolean blockRead;

  public static void main(String[] args) throws Exception {
    URL.setURLStreamHandlerFactory(
        protocol ->
            !"https".equals(protocol)
                ? null
                : new URLStreamHandler() {
                  protected URLConnection openConnection(URL url) {
                    opened.incrementAndGet();
                    return latest = new FakeConnection(url, blockRead);
                  }
                });
    CancellationToken cancelled = new CancellationToken();
    cancelled.cancel();
    try {
      post(cancelled);
      throw new AssertionError("cancelled POST must fail");
    } catch (CancellationException expected) {
    }
    if (opened.get() != 0) throw new AssertionError("pre-cancelled call opened network connection");

    CancellationToken active = new CancellationToken();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    blockRead = true;
    Future<Http.Reply> pending = executor.submit(() -> post(active));
    try {
      long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while ((latest == null || latest.readEntered.getCount() != 0) && System.nanoTime() < until)
        Thread.sleep(5);
      if (latest == null || latest.readEntered.getCount() != 0)
        throw new AssertionError("read never started");
      active.cancel();
      try {
        pending.get(2, TimeUnit.SECONDS);
        throw new AssertionError("cancelled read returned a successful reply");
      } catch (ExecutionException expected) {
        if (!(expected.getCause() instanceof CancellationException)) throw expected;
      }
      if (latest.disconnections.get() == 0)
        throw new AssertionError("cancel did not disconnect blocked HTTP");
    } finally {
      active.cancel();
      executor.shutdownNow();
    }

    blockRead = false;
    CancellationToken completed = new CancellationToken();
    if (post(completed).code != 200) throw new AssertionError();
    int disconnected = latest.disconnections.get();
    completed.cancel();
    if (latest.disconnections.get() != disconnected)
      throw new AssertionError("completed POST retained cancellation callback");
    Http.post("https://unit.invalid/test", "{}", "application/json", Collections.emptyMap());
    System.out.println("HttpCancellationTest passed");
  }

  private static Http.Reply post(CancellationToken token) throws Exception {
    return Http.post(
        "https://unit.invalid/test", "{}", "application/json", Collections.emptyMap(), token);
  }

  private static final class FakeConnection extends HttpURLConnection {
    final CountDownLatch readEntered = new CountDownLatch(1), released = new CountDownLatch(1);
    final AtomicInteger disconnections = new AtomicInteger();
    final boolean blocked;

    FakeConnection(URL url, boolean blocked) {
      super(url);
      this.blocked = blocked;
    }

    public void connect() {}

    public boolean usingProxy() {
      return false;
    }

    public void disconnect() {
      disconnections.incrementAndGet();
      released.countDown();
    }

    public OutputStream getOutputStream() {
      return new ByteArrayOutputStream();
    }

    public int getResponseCode() {
      return 200;
    }

    public InputStream getInputStream() {
      return new InputStream() {
        public int read() throws IOException {
          readEntered.countDown();
          if (!blocked) return -1;
          try {
            if (!released.await(4, TimeUnit.SECONDS))
              throw new IOException("disconnect did not unblock read");
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException(interrupted);
          }
          throw new IOException("disconnected");
        }
      };
    }
  }
}
