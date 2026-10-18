package local.tvgpt.assistant;

import org.json.*;

public class AgentHarnessTest {
  static JSONArray call(String id, String name, String args) throws Exception {
    return new JSONArray()
        .put(
            new JSONObject()
                .put("type", "function_call")
                .put("call_id", id)
                .put("name", name)
                .put("arguments", args));
  }

  public static void main(String[] args) throws Exception {
    Http.reset(0);
    Http.scripted =
        new JSONArray[] {
          call("same", "navigate", "{}"),
          call("same", "navigate", "{}"),
          call(
              "finish",
              "task_result",
              "{\"status\":\"unconfirmed\",\"speech\":\"Не удалось"
                  + " подтвердить.\",\"evidence_ids\":[]}")
        };
    TaskHarness h = new TaskHarness();
    TaskHarness.Task t = h.admit("task", "u1", "Открой", new JSONArray());
    String result = new Agent(null, SessionWorkspace.open(null), null, h, t).run("Открой");
    if (TvTools.actions != 1 || !result.equals("Не удалось подтвердить."))
      throw new AssertionError("duplicate or false success: " + result);
    if (!Http.lastInstructions.contains("task_result")
        || !Http.lastInstructions.contains("receipt_id"))
      throw new AssertionError("Outcome protocol missing in model instructions");
    boolean visibleReceipt = false;
    for (int i = 0; i < Http.lastInput.length(); i++) {
      JSONObject item = Http.lastInput.getJSONObject(i);
      if (item.optString("type").equals("function_call_output")) {
        try {
          visibleReceipt |= new JSONObject(item.optString("output")).has("receipt_id");
        } catch (Exception ignored) {
        }
      }
    }
    if (!visibleReceipt)
      throw new AssertionError("Model cannot see runtime receipt IDs in tool content");
    Http.reset(0);
    TaskHarness.Task cancelled = h.admit("cancelled", "u2", "Стоп", new JSONArray());
    Http.beforePost = () -> cancelled.token.cancel();
    try {
      new Agent(null, SessionWorkspace.open(null), null, h, cancelled).run("Стоп");
      throw new AssertionError("cancel ignored");
    } catch (java.util.concurrent.CancellationException expected) {
    }
    if (TvTools.actions != 0) throw new AssertionError("cancelled action dispatched");
    Http.reset(0);
    TaskHarness screenTasks = new TaskHarness();
    TaskHarness.Task owner = screenTasks.admit("owner", "u1", "find", new JSONArray());
    screenTasks.claimScreen(owner);
    TaskHarness.Task competing = screenTasks.admit("competing", "u2", "next", new JSONArray());
    Http.scripted =
        new JSONArray[] {
          call("blocked-read", "read_screen", "{}"),
          call("blocked-action", "navigate", "{}"),
          call(
              "finish",
              "task_result",
              "{\"status\":\"unconfirmed\",\"speech\":\"Не подтверждено\",\"evidence_ids\":[]}")
        };
    new Agent(null, SessionWorkspace.open(null), null, screenTasks, competing).run("next");
    if (TvTools.actions != 0) throw new AssertionError("Competing task used stale screen");
    if (!screenTasks.screenOwner().equals(owner.id))
      throw new AssertionError("Finishing a blocked task released the owner's screen");
    Http.reset(0);
    Http.scripted = new JSONArray[] {call("end", "end_conversation", "{}")};
    boolean[] ended = {false};
    TaskHarness.Task ending = screenTasks.admit("ending", "u3", "finish", new JSONArray());
    new Agent(null, SessionWorkspace.open(null), () -> ended[0] = true, screenTasks, ending)
        .run("finish");
    if (!ended[0] || TvTools.actions != 0)
      throw new AssertionError("Closing was blocked by a screen task");
    screenTasks.releaseScreen(owner);
    Http.reset(0);
    TaskHarness.Task after = screenTasks.admit("after", "u4", "navigate", new JSONArray());
    Http.scripted =
        new JSONArray[] {
          call("move", "navigate", "{}"),
          call(
              "finish",
              "task_result",
              "{\"status\":\"unconfirmed\",\"speech\":\"Не подтверждено\",\"evidence_ids\":[]}")
        };
    new Agent(null, SessionWorkspace.open(null), null, screenTasks, after).run("navigate");
    if (TvTools.actions != 1 || !screenTasks.screenOwner().isEmpty())
      throw new AssertionError("Screen was not reusable after task finished");
    Http.reset(0);
    TaskHarness selfHarness = new TaskHarness();
    TaskHarness.Task self = selfHarness.admit("self", "request", "home", new JSONArray());
    selfHarness.admit("self-alias", "request", "home", new JSONArray());
    Http.scripted =
        new JSONArray[] {
          call("cancel-self", "task_cancel", "{\"task_id\":\"self\"}"),
          call("cancel-alias", "task_cancel", "{\"task_id\":\"self-alias\"}"),
          call("actual-home", "navigate", "{\"direction\":\"home\"}"),
          call(
              "result",
              "task_result",
              "{\"status\":\"unconfirmed\",\"speech\":\"Проверю\",\"evidence_ids\":[]}")
        };
    new Agent(null, SessionWorkspace.open(null), null, selfHarness, self).run("home");
    if (self.token.cancelled() || TvTools.actions != 1)
      throw new AssertionError("Mistaken self-cancellation killed the current user request");
    System.out.println(
        "AgentHarnessTest: duplicate tool receipts, grounded finish and post-auth/request"
            + " cancellation passed");
  }
}
