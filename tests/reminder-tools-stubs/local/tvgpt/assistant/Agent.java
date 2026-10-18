package local.tvgpt.assistant;

import org.json.*;

/** Schema envelope only; the real ReminderTools declarations are under test. */
final class Agent {
  static JSONObject tool(String name, String description, JSONObject properties) throws Exception {
    return new JSONObject()
        .put("name", name)
        .put("parameters", new JSONObject().put("properties", properties));
  }
}
