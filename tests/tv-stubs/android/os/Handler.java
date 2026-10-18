package android.os;

import java.util.concurrent.CountDownLatch;

public final class Handler {
  public static volatile boolean blocked;
  public static volatile Runnable pending;
  public static CountDownLatch posted = new CountDownLatch(1);

  public Handler(Looper looper) {}

  public boolean post(Runnable task) {
    if (blocked) {
      pending = task;
      posted.countDown();
    } else task.run();
    return true;
  }
}
