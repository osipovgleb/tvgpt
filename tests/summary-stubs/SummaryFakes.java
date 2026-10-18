package local.tvgpt.assistant;

import java.util.*;
import org.json.*;

class ArchiveEvents {
  static void changed(android.content.Context c) {}
}

class AccountStore {
  static boolean loggedIn = true;

  AccountStore(android.content.Context c) {}

  JSONObject fresh() {
    return new JSONObject();
  }

  JSONObject load() {
    return loggedIn ? new JSONObject() : null;
  }
}

class LiveConnection {
  static Map<String, String> headers(JSONObject account) {
    return new HashMap<>();
  }
}

class Http {
  static String lastTier;
  static Runnable during;
  static int requests;

  static class Reply {
    String body;

    void requireSuccess() {}
  }

  static Reply post(String u, String b, String type, Map<String, String> h) throws Exception {
    requests++;
    JSONObject request = new JSONObject(b);
    lastTier = request.optString("service_tier");
    if (request.has("tools")) throw new AssertionError("summary must not expose tools");
    if (during != null) during.run();
    Reply r = new Reply();
    r.body =
        "{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"Тема\\nОписание\"}]}]}";
    return r;
  }
}

class LiveProtocol {
  static JSONObject completedResponse(String s) throws Exception {
    return new JSONObject(s);
  }
}

class ModelSettings {
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
