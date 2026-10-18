package local.tvgpt.assistant.digests;

import java.util.concurrent.atomic.*;

public final class DigestJobHandoffTest {
  public static void main(String[] args) {
    DigestJobHandoff handoff = new DigestJobHandoff();
    Object token = new Object();
    handoff.started(token);
    AtomicBoolean pending = new AtomicBoolean();
    AtomicInteger schedules = new AtomicInteger();
    handoff.request(pending::get, schedules::incrementAndGet);
    handoff.completed(token, () -> pending.set(true), pending::get, schedules::incrementAndGet);
    if (schedules.get() != 1) throw new AssertionError("completion lost newly queued work");
    pending.set(true);
    handoff.request(pending::get, schedules::incrementAndGet);
    if (schedules.get() != 2) throw new AssertionError("stale system job hides queue");
    System.out.println("DigestJobHandoffTest passed");
  }
}
