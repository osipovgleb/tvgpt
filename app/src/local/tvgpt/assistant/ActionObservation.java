package local.tvgpt.assistant;

import org.json.JSONObject;

/** Evidence has a scope: a visible package does not prove a search or video selection. */
final class ActionObservation {
  static boolean samePackage(JSONObject snapshot, String expected) {
    return !expected.isEmpty() && expected.equals(snapshot.optString("package"));
  }

  static boolean changed(JSONObject before, JSONObject after) {
    String pkg = after.optString("package");
    if (pkg.isEmpty()) return false;
    if (!pkg.equals(before.optString("package"))) return true;
    String text = after.optString("text"), focus = after.optString("focus");
    return (!text.isEmpty() && !text.equals(before.optString("text")))
        || (!focus.isEmpty() && !focus.equals(before.optString("focus")));
  }

  static JSONObject result(String receipt, JSONObject snapshot, String scope) throws Exception {
    return new JSONObject()
        .put("receipt", receipt)
        .put("status", scope.isEmpty() ? "unconfirmed" : "observed")
        .put("confirmed_scope", scope)
        .put("observation", snapshot)
        .put(
            "instruction",
            "Подтверждай только наблюдаемый результат, а не всю цель по факту отправки команды."
                + " Если состояния не видно, результат не подтверждён, это не доказательство"
                + " ошибки.");
  }
}
