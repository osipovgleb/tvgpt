package local.tvgpt.assistant;

import java.util.*;
import org.json.*;

class AccountStore {
  AccountStore(android.content.Context c) {}

  JSONObject fresh() {
    return new JSONObject();
  }
}

class LiveConnection {
  static Map<String, String> headers(JSONObject a) {
    return new LinkedHashMap<>();
  }
}

class SessionWorkspace {
  static SessionStore.State open(android.content.Context c) {
    SessionStore.State s = new SessionStore.State();
    s.id = "test-thread";
    s.history = new JSONArray();
    s.transcript = new JSONArray();
    return s;
  }

  static void save(SessionStore.State s) {}
}

class TvTools {
  static int actions;

  TvTools(android.content.Context c) {}

  String execute(String name, JSONObject args) {
    actions++;
    return "Нажатие выполнено";
  }
}

class ScreenCapture {
  static JSONArray capture() {
    return new JSONArray();
  }
}

class Http {
  static String lastTier, lastEffort, lastRouting;
  static String toolName = "navigate";
  static JSONArray lastInput;
  static volatile Runnable beforePost;
  static int requests, requestedActions;
  static boolean finalized;
  static JSONArray[] scripted;

  static class Reply {
    int code = 200;
    String body;

    void requireSuccess() {}
  }

  static Reply post(String url, String body, String type, Map<String, String> headers)
      throws Exception {
    if (beforePost != null) beforePost.run();
    JSONObject request = new JSONObject(body);
    lastRouting = headers.get("x-codex-routing-hint");
    lastTier = request.optString("service_tier");
    lastEffort =
        request.optJSONObject("reasoning") == null
            ? ""
            : request.getJSONObject("reasoning").optString("effort");
    lastInput = new JSONArray(request.getJSONArray("input").toString());
    for (int i = 0; i < lastInput.length(); i++) {
      JSONObject entry = lastInput.getJSONObject(i);
      if (entry.optString("type").equals("function_call_output")
          && !(entry.opt("output") instanceof String)
          && !(entry.opt("output") instanceof JSONArray))
        throw new AssertionError("Invalid Responses tool output: expected string or content array");
    }
    requests++;
    boolean finalOnly = request.optString("tool_choice").equals("none");
    JSONArray output = new JSONArray();
    if (TvTools.actions < requestedActions && !finalOnly) {
      output.put(
          new JSONObject()
              .put("type", "function_call")
              .put("call_id", "test_" + requests)
              .put("name", toolName)
              .put("arguments", "{\"direction\":\"right\",\"count\":1}"));
    } else {
      finalized = finalOnly;
      output.put(
          new JSONObject()
              .put("type", "message")
              .put(
                  "content",
                  new JSONArray()
                      .put(
                          new JSONObject()
                              .put("type", "output_text")
                              .put(
                                  "text",
                                  finalOnly ? "Этап завершён, можно продолжить." : "Готово"))));
    }
    if (scripted != null) output = scripted[Math.min(requests - 1, scripted.length - 1)];
    Reply result = new Reply();
    result.body =
        "data: "
            + new JSONObject()
                .put("type", "response.completed")
                .put("response", new JSONObject().put("output", output))
            + "\n\n";
    return result;
  }

  static void reset(int count) {
    requests = 0;
    scripted = null;
    AssistantSkills.loads = 0;
    toolName = "navigate";
    requestedActions = count;
    finalized = false;
    TvTools.actions = 0;
  }
}

class KnowledgeTools {
  static Object execute(android.content.Context c, String n, JSONObject a) {
    TvTools.actions++;
    return new JSONObject().put("saved_id", 12);
  }

  static void schemas(JSONArray a) {}
}

class ReminderTools {
  static java.time.ZonedDateTime currentTime(android.content.Context c) {
    return java.time.ZonedDateTime.now();
  }

  static Object execute(android.content.Context c, String n, JSONObject a) {
    return "ok";
  }

  static void schemas(JSONArray a) {}
}

class AssistantSkills {
  static int loads;

  static String app(android.content.Context context, String name) {
    loads++;
    return "Bundled app skill";
  }

  static String search(android.content.Context context) {
    return "search policy";
  }
}

class ModelSettings {
  static void recordTier(
      android.content.Context c, String model, String requested, String served) {}

  static String effort = "";

  static String reasoning(android.content.Context c, String kind) {
    return effort;
  }

  static String tier = "default";

  static String serviceTier(android.content.Context c) {
    return tier;
  }

  static String discussion(android.content.Context c) {
    return "gpt-6.1-sol";
  }

  static String summary(android.content.Context c) {
    return "gpt-6.1-sol";
  }

  static String live(android.content.Context c) {
    return "gpt-live-1-codex";
  }
}
