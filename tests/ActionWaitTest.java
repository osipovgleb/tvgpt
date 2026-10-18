package local.tvgpt.assistant;

public final class ActionWaitTest {
  static long now;
  static final ActionWait.Time TIME =
      new ActionWait.Time() {
        public long now() {
          return now;
        }

        public void sleep(long millis) {
          now += millis;
        }
      };

  public static void main(String[] args) throws Exception {
    now = 0;
    if (!ActionWait.until(() -> true, 5000, TIME) || now != 0)
      throw new AssertionError("Ready condition must return immediately");
    if (!ActionWait.until(() -> now >= 450, 5000, TIME) || now >= 1000)
      throw new AssertionError("Wait did not stop when actual state arrived");
    now = 0;
    if (ActionWait.until(() -> false, 500, TIME) || now != 500)
      throw new AssertionError("Unconfirmed state must remain unconfirmed at bound");
    Thread.currentThread().interrupt();
    try {
      ActionWait.until(() -> false, 500, TIME);
      throw new AssertionError("Cancelled action kept waiting");
    } catch (InterruptedException expected) {
      Thread.interrupted();
    }
    System.out.println(
        "Action waits: early completion, delayed state, bounds and cancellation passed");
  }
}
