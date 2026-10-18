package local.tvgpt.assistant;

/** Keep one call's focus alive without fighting another player in an unbounded retry loop. */
final class AudioFocusLease {
  private boolean active;
  private long window, last;
  private int retries;

  void open() {
    active = true;
    retries = 0;
    window = -1;
    last = -1000;
  }

  void close() {
    active = false;
  }

  long delay(long now) {
    if (!active) return -1;
    if (window < 0 || now - window >= 10000) return 0;
    if (retries >= 3) return -1;
    return Math.max(0, 1000 - (now - last));
  }

  boolean reclaim(long now) {
    if (!active) return false;
    if (window < 0 || now - window >= 10000) {
      window = now;
      retries = 0;
    }
    if (retries >= 3 || now - last < 1000) return false;
    retries++;
    last = now;
    return true;
  }
}
