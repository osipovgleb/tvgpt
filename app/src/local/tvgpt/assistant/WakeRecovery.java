package local.tvgpt.assistant;

/** Recorder retries belong to one explicit start and cannot survive stop or disposal. */
final class WakeRecovery {
  private long generation;
  private boolean wanted, closed;
  private int retries;

  synchronized long start() {
    if (closed || wanted) return -1;
    wanted = true;
    retries = 0;
    return ++generation;
  }

  synchronized void stop() {
    wanted = false;
    generation++;
  }

  synchronized void close() {
    closed = true;
    stop();
  }

  synchronized boolean active(long ticket) {
    return wanted && !closed && generation == ticket;
  }

  synchronized boolean consume(long ticket) {
    if (!active(ticket)) return false;
    stop();
    return true;
  }

  synchronized long retryDelay(long ticket) {
    if (!active(ticket)) return -1;
    long[] delays = {500, 1500, 4000};
    return retries < delays.length ? delays[retries++] : -1;
  }
}
