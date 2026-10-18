package local.tvgpt.assistant;

import org.json.JSONObject;

public final class ActionObservationTest {
  public static void main(String[] args) throws Exception {
    JSONObject empty = new JSONObject(), app = new JSONObject().put("package", "youtube");
    if (ActionObservation.samePackage(empty, "") || ActionObservation.samePackage(empty, "youtube"))
      throw new AssertionError("Missing foreground must never verify a launch");
    if (!ActionObservation.samePackage(app, "youtube"))
      throw new AssertionError("Visible target ignored");
    if (ActionObservation.changed(app, app) || ActionObservation.changed(app, empty))
      throw new AssertionError("Unchanged or missing tree is not confirmation");
    if (!ActionObservation.changed(app, new JSONObject(app.toString()).put("focus", "second card")))
      throw new AssertionError("Focus evidence ignored");
    JSONObject pending = ActionObservation.result("sent", empty, "");
    if (!pending.getString("status").equals("unconfirmed"))
      throw new AssertionError("Timeout became success/failure");
    JSONObject seen = ActionObservation.result("sent", app, "app_visible");
    if (!seen.getString("confirmed_scope").equals("app_visible"))
      throw new AssertionError("Verification scope lost");
    System.out.println("Action observations: absent, unchanged, scoped and focus evidence passed");
  }
}
