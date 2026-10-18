package local.tvgpt.assistant;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class RemoteSequenceTest {
  private static JSONObject step(String direction, Object count) throws Exception {
    return new JSONObject().put("direction", direction).put("count", count);
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static final class Clock implements ActionWait.Time {
    long now;
    int sleeps;

    public long now() {
      return now;
    }

    public void sleep(long millis) {
      now += millis;
      sleeps++;
    }
  }

  private static void reject(JSONArray steps) throws Exception {
    List<String> sent = new ArrayList<>();
    try {
      RemoteSequence.execute(steps, sent::add, new Clock());
      throw new AssertionError("Invalid sequence accepted: " + steps);
    } catch (IllegalArgumentException expected) {
      check(sent.isEmpty(), "Sequence partially sent before validation");
    }
  }

  public static void main(String[] args) throws Exception {
    List<String> sent = new ArrayList<>();
    Clock clock = new Clock();
    String receipt =
        RemoteSequence.execute(
            new JSONArray().put(step("up", 1)).put(step("right", 5)).put(step("select", 1)),
            sent::add,
            clock);
    check(
        sent.equals(List.of("up", "right", "right", "right", "right", "right", "select")),
        "Ordered transient-menu sequence changed");
    check(clock.sleeps == 6 && clock.now == 600, "Per-step observation or trailing wait added");
    check(receipt.contains("7"), "Sent count absent");
    reject(new JSONArray());
    reject(new JSONArray().put(step("home", 1)));
    reject(new JSONArray().put(step("back", 1)));
    reject(new JSONArray().put(step("select", 2)));
    reject(new JSONArray().put(step("select", 1)).put(step("right", 1)));
    reject(new JSONArray().put(step("right", 0)));
    reject(new JSONArray().put(step("right", 21)));
    reject(new JSONArray().put(step("right", 20)).put(step("left", 11)));
    reject(new JSONArray().put(step("right", 1.5)));
    reject(new JSONArray().put(step("right", "5")));
    reject(
        new JSONArray()
            .put(step("right", 1))
            .put(step("left", 1))
            .put(step("right", 1))
            .put(step("left", 1))
            .put(step("right", 1))
            .put(step("left", 1))
            .put(step("up", 1))
            .put(step("down", 1))
            .put(step("right", 1)));
    reject(new JSONArray().put(step("up", 1)).put(step("right", 1).put("shell", "id")));
    reject(new JSONArray().put(step("up", 1)).put(new JSONObject().put("direction", "right")));
    reject(new JSONArray().put("right"));
    sent.clear();
    receipt =
        RemoteSequence.execute(
            new JSONArray().put(step("right", 3)),
            direction -> {
              if (sent.size() == 1) throw new Exception("remote unavailable");
              sent.add(direction);
            },
            new Clock());
    check(sent.size() == 1 && receipt.contains("1 из 3"), "Failure retried or count lost");
    sent.clear();
    try {
      RemoteSequence.execute(
          new JSONArray().put(step("right", 3)),
          direction -> {
            sent.add(direction);
            Thread.currentThread().interrupt();
          },
          new Clock());
      throw new AssertionError("Interrupted sequence continued");
    } catch (InterruptedException expected) {
      check(sent.size() == 1 && Thread.currentThread().isInterrupted(), "Cancellation lost");
    } finally {
      Thread.interrupted();
    }
    System.out.println(
        "Remote sequences: ordered batch, bounds, final-select-only, validation-before-send and"
            + " cancellation passed");
  }
}
