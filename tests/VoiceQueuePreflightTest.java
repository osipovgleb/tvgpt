package local.tvgpt.assistant;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import local.tvgpt.assistant.voice.ClosingWork;

public final class VoiceQueuePreflightTest {
  public static void main(String[] args) throws Exception {
    List<Runnable> queue = new ArrayList<>();
    List<String> events = new ArrayList<>();
    AtomicBoolean closed = new AtomicBoolean();
    ClosingWork.submit(queue::add, closed::get, () -> events.add("mute"), true);
    ClosingWork.submit(queue::add, closed::get, () -> events.add("offer"), false);
    closed.set(true);
    queue.forEach(Runnable::run);
    if (!events.equals(Arrays.asList("mute")))
      throw new AssertionError("queued mute must survive close, ordinary work must not");
    events.clear();
    if (VoicePreflight.ready(() -> false, () -> events.add("exit"))) throw new AssertionError();
    if (!events.equals(Arrays.asList("exit")))
      throw new AssertionError("missing account must dismiss orb");
    events.clear();
    if (VoicePreflight.ready(
        () -> {
          throw new Exception("keystore unavailable");
        },
        () -> events.add("exit"))) throw new AssertionError();
    if (!events.equals(Arrays.asList("exit")))
      throw new AssertionError("unreadable account must dismiss orb");
    events.clear();
    if (!VoicePreflight.ready(() -> true, () -> events.add("exit")) || !events.isEmpty())
      throw new AssertionError("valid account must continue quietly");
    System.out.println("VoiceQueuePreflightTest passed");
  }
}
