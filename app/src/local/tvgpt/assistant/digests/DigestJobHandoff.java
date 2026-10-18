package local.tvgpt.assistant.digests;

import java.util.*;
import java.util.function.BooleanSupplier;

/** In-process ownership complements Android's asynchronously updated pending-job snapshot. */
public final class DigestJobHandoff {
  private final Set<Object> active = new HashSet<>();

  public synchronized void started(Object token) {
    active.add(token);
  }

  public synchronized void stopped(Object token) {
    active.remove(token);
  }

  public synchronized void request(BooleanSupplier pending, Runnable schedule) {
    if (active.isEmpty() && pending.getAsBoolean()) schedule.run();
  }

  public synchronized void completed(
      Object token, Runnable finish, BooleanSupplier pending, Runnable schedule) {
    active.remove(token);
    finish.run();
    request(pending, schedule);
  }
}
