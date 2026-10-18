package local.tvgpt.assistant;

/** Bounded observation, not a timeout-to-failure conversion. Never run on the UI thread. */
final class ActionWait {
  interface Condition {
    boolean observed() throws Exception;
  }

  interface Time {
    long now();

    void sleep(long millis) throws InterruptedException;
  }

  static final Time MONOTONIC =
      new Time() {
        public long now() {
          return System.nanoTime() / 1000000;
        }

        public void sleep(long millis) throws InterruptedException {
          Thread.sleep(millis);
        }
      };

  static boolean until(Condition condition, long budget, Time time) throws Exception {
    long deadline = time.now() + budget;
    while (true) {
      if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
      if (condition.observed()) return true;
      long remaining = deadline - time.now();
      if (remaining <= 0) return false;
      time.sleep(Math.min(150, remaining));
    }
  }
}
