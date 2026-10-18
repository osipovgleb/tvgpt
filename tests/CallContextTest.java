package local.tvgpt.assistant;

import org.json.*;

public class CallContextTest {
  private static JSONObject turn(String id, String role, String text) throws Exception {
    return new JSONObject()
        .put("type", "turn.done")
        .put("turn", new JSONObject().put("id", id).put("role", role).put("transcript", text));
  }

  public static void main(String[] args) throws Exception {
    CallContext c = new CallContext();
    JSONObject first = turn("first", "user", "Find a film");
    first.getJSONObject("turn").put("start_ms", 100);
    c.accept(first);
    if (!c.isCurrent("first")) throw new AssertionError("Fresh result silenced");
    JSONObject next = turn("next", "user", "Finish");
    next.put("type", "turn.created");
    next.getJSONObject("turn").put("start_ms", 200);
    c.accept(next);
    if (c.isCurrent("first") || !c.isCurrent("next"))
      throw new AssertionError("Old task can speak over a new user turn");
    c.accept(first); // delayed mirrored final must not reclaim current speech
    if (c.isCurrent("first")) throw new AssertionError("Mirrored old final rewound turn ownership");
    c = new CallContext();
    c.accept(turn("u1", "user", "Мой рейс в 18:37"));
    c.accept(turn("u1", "user", "Мой рейс в 18:37"));
    c.accept(turn("a1", "assistant", "Учту."));
    JSONArray snapshot = c.snapshot("u2", "Напомни об этом за час");
    if (snapshot.length() != 2 || !snapshot.toString().contains("18:37"))
      throw new AssertionError("Current speech lost or duplicated");
    if (c.snapshot("u1", "Мой рейс в 18:37").length() != 0)
      throw new AssertionError("Future turns leaked into earlier request");
    c.accept(turn("future", "user", "Рейс отменили"));
    if (snapshot.toString().contains("отменили"))
      throw new AssertionError("Snapshot mutated with future speech");
    c.accept(turn("u2", "user", "Напомни об этом за час"));
    if (c.snapshot("u2", "Напомни об этом за час").toString().contains("Напомни об этом"))
      throw new AssertionError("Request duplicated in context");
    for (int i = 0; i < 80; i++) c.accept(turn("x" + i, "user", "я".repeat(3000)));
    JSONArray bounded = c.snapshot("next", "проверка");
    if (bounded.length() > 16 || bounded.toString().length() > 10000)
      throw new AssertionError("Unbounded context");
    if (new CallContext().snapshot("new", "Привет").length() != 0)
      throw new AssertionError("Fresh call inherited memory");
    System.out.println(
        "CallContextTest: current speech, mirrored finals, immutable cutoffs, request exclusion and"
            + " bounds passed");
  }
}
