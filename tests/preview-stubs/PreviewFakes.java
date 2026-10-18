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
  static Map<String, String> headers(JSONObject j) {
    return new HashMap<>();
  }
}

class AssistantService {
  static Object owner;

  static void conversationStarted(Object o) {
    owner = o;
  }

  static void conversationEnded(Object o) {
    if (owner == o) owner = null;
  }
}

class VoicePanel {
  static Object active;
}

class Protocol {
  static String callId(String s) {
    return s;
  }
}

class LiveProtocol {
  static JSONObject speechOutput(String s) {
    return new JSONObject();
  }

  static JSONObject userInput(String s) {
    return new JSONObject();
  }
}

class DeviceLogin {
  static String safeError(Exception e) {
    return "error";
  }
}

class Http {
  static class Reply {
    int code;
    String body, location;

    void requireSuccess() {}
  }

  static Reply post(String u, String b, String t, Map<String, String> h) {
    throw new AssertionError("Preview lifecycle test must not use network");
  }
}

class VoiceSamplePlayer {
  static Object owner() {
    return null;
  }

  static void stopActive() {}
}

class ModelSettings {
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
