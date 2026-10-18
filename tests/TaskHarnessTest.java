package local.tvgpt.assistant;

import org.json.*;

public class TaskHarnessTest {
  public static void main(String[] args) throws Exception {
    TaskHarness h = new TaskHarness();
    TaskHarness.Task a = h.admit("d1", "u1", "найди", new JSONArray());
    if (a == null || h.admit("d1", "u1", "найди", new JSONArray()) != null)
      throw new AssertionError("mirror replay");
    if (h.admit("d2", "u2", "найди", new JSONArray()) == null)
      throw new AssertionError("deliberate repeat lost");
    a.receipt("call1", "ok");
    if (!"ok".equals(a.receipt("call1"))) throw new AssertionError("receipt lost");
    h.cancel(a.id);
    if (!a.token.cancelled()) throw new AssertionError("cancel not propagated");
    TaskHarness.Task b = h.admit("d3", "u3", "другое", new JSONArray());
    for (int i = 0; i < 150; i++) {
      TaskHarness.Task x = h.admit("long" + i, "turn" + i, "work", new JSONArray());
      if (x == null) throw new AssertionError("lifetime cap");
      x.complete("ok");
    }
    if (h.admit("alias", "u3", "другое", new JSONArray()) != null)
      throw new AssertionError("same input turn repeated");
    TaskHarness.Task delivery = h.admit("original", "new-input", "one", new JSONArray());
    h.admit("mirror-alias", "new-input", "one", new JSONArray());
    if (delivery.complete("receipt").size() != 2 || h.result("mirror-alias") != null)
      throw new AssertionError("alias delivered twice");
    h.admit("late-alias", "new-input", "one", new JSONArray());
    if (!"receipt".equals(h.result("late-alias")) || h.result("late-alias") != null)
      throw new AssertionError("late alias receipt");
    TaskHarness screens = new TaskHarness();
    TaskHarness.Task first = screens.admit("first", "u1", "Find a film", new JSONArray());
    TaskHarness.Task second = screens.admit("second", "u2", "Choose episode one", new JSONArray());
    if (!screens.claimScreen(first) || screens.claimScreen(second))
      throw new AssertionError("Concurrent goals can navigate the same screen");
    // A single tool finishing must not release the whole navigation transaction.
    if (screens.claimScreen(second)) throw new AssertionError("Screen owner lost between tools");
    first.cancel();
    if (screens.claimScreen(second))
      throw new AssertionError("Cancellation released screen before in-flight tool unwound");
    screens.releaseScreen(first);
    if (!screens.claimScreen(second)) throw new AssertionError("Screen ownership leaked");
    screens.releaseScreen(first);
    if (!screens.claimScreen(second))
      throw new AssertionError("Late cleanup released another task");
    screens.releaseScreen(second);
    h.close();
    if (!b.token.cancelled()) throw new AssertionError("close leaked task");
    if (h.admit("late", "u4", "x", new JSONArray()) != null) throw new AssertionError("late task");
    System.out.println(
        "TaskHarnessTest: mirrors, intentional repeats, receipts, cancellation and close passed");
  }
}
