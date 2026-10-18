package local.tvgpt.assistant;

import java.util.*;
import org.json.*;

final class LiveProtocol {
  // A dedicated command avoids sending a goodbye through the expensive agent loop.
  static boolean localEndDelegation(String text) {
    return text != null
        && (text.trim().equals("TVGPT_END_CONVERSATION") || EndPhrase.matches(text));
  }

  static String localMediaAction(String text) {
    if (text == null) return null;
    if (text.trim().equals("TVGPT_MEDIA_PAUSE")) return "pause";
    if (text.trim().equals("TVGPT_MEDIA_PLAY")) return "play";
    String words = EndPhrase.normalized(text);
    if (java.util.Arrays.asList(
            "пауза", "поставь на паузу", "поставь видео на паузу", "приостанови видео")
        .contains(words)) return "pause";
    if (java.util.Arrays.asList(
            "продолжи воспроизведение", "возобнови воспроизведение", "продолжи видео")
        .contains(words)) return "play";
    return null;
  }

  static boolean endsConversation(JSONObject event) {
    JSONObject turn = event.optJSONObject("turn");
    return event.optString("type").equals("turn.done")
        && turn != null
        && turn.optString("role").equals("user")
        && EndPhrase.finalTranscript(turn.optString("transcript"));
  }

  static JSONObject userInput(String text) throws Exception {
    return new JSONObject()
        .put("type", "session.context.append")
        .put(
            "content",
            new JSONArray().put(new JSONObject().put("type", "input_text").put("text", text)));
  }

  static JSONObject speechOutput(String text) throws Exception {
    return userInput(text).put("channel", "speakable");
  }

  static String delegationText(JSONObject event) throws Exception {
    JSONObject item = event.optJSONObject("item");
    if (!event.optString("type").equals("delegation.created")
        || item == null
        || !item.optString("type").equals("delegation")
        || !item.optString("target").equals("client")
        || item.optString("id").isEmpty()) return null;
    JSONArray content = item.optJSONArray("content");
    StringBuilder text = new StringBuilder();
    if (content != null)
      for (int i = 0; i < content.length(); i++) {
        JSONObject part = content.optJSONObject(i);
        if (part != null && part.optString("type").equals("input_text"))
          text.append(part.optString("text"));
      }
    return text.toString();
  }

  static List<String> chunks(String text) throws Exception {
    List<String> parts = new ArrayList<>();
    StringBuilder part = new StringBuilder();
    int bytes = 0;
    for (int i = 0; i < text.length(); ) {
      int cp = text.codePointAt(i);
      String c = new String(Character.toChars(cp));
      i += Character.charCount(cp);
      int size = c.getBytes("UTF-8").length;
      if (bytes + size > 500) {
        parts.add(part.toString());
        part.setLength(0);
        bytes = 0;
      }
      part.append(c);
      bytes += size;
    }
    if (part.length() > 0) parts.add(part.toString());
    return parts;
  }

  static JSONObject completedResponse(String sse) throws Exception {
    java.util.SortedMap<Integer, JSONObject> completed = new java.util.TreeMap<>();
    for (String line : sse.split("\n")) {
      if (!line.startsWith("data:")) continue;
      String body = line.substring(5).trim();
      if (body.equals("[DONE]") || body.isEmpty()) continue;
      JSONObject event = new JSONObject(body);
      if (event.optString("type").equals("response.output_item.done")) {
        int index = event.optInt("output_index", -1);
        JSONObject item = event.optJSONObject("item");
        if (index >= 0 && index < 256 && item != null) completed.put(index, item);
      }
      if (event.optString("type").equals("response.completed")) {
        JSONObject response = event.getJSONObject("response");
        JSONArray output = response.optJSONArray("output");
        if ((output == null || output.length() == 0) && !completed.isEmpty()) {
          output = new JSONArray();
          for (JSONObject item : completed.values()) output.put(item);
          response.put("output", output);
        }
        return response;
      }
    }
    return null;
  }
}
