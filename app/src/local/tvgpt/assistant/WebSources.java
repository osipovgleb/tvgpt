package local.tvgpt.assistant;

import java.util.LinkedHashSet;
import org.json.*;

/** Bounded source links from server citations; no page fetches or instructions. */
final class WebSources {
  static void archive(android.content.Context context, String conversation, JSONObject response) {
    String sources = extract(response.optJSONArray("output"));
    if (sources.isEmpty()) return;
    try {
      new local.tvgpt.assistant.data.ConversationRepository(context)
          .appendTurn(
              conversation,
              "web",
              response.optString("id", java.util.UUID.randomUUID().toString()),
              System.currentTimeMillis(),
              "assistant",
              sources);
    } catch (Exception error) {
      android.util.Log.w(
          "TVGPTAgent", "Source archive unavailable: " + error.getClass().getSimpleName());
    }
  }

  static String extract(JSONArray output) {
    if (output == null) return "";
    LinkedHashSet<String> urls = new LinkedHashSet<>();
    for (int i = 0; i < output.length() && urls.size() < 8; i++) {
      JSONObject item = output.optJSONObject(i);
      JSONArray content = item == null ? null : item.optJSONArray("content");
      for (int j = 0; content != null && j < content.length() && urls.size() < 8; j++) {
        JSONObject part = content.optJSONObject(j);
        JSONArray annotations = part == null ? null : part.optJSONArray("annotations");
        for (int k = 0; annotations != null && k < annotations.length() && urls.size() < 8; k++) {
          JSONObject annotation = annotations.optJSONObject(k);
          if (annotation == null || !annotation.optString("type").equals("url_citation")) continue;
          String url = annotation.optString("url");
          if (url.length() <= 2048 && url.matches("https?://[^\\s]+")) urls.add(url);
        }
      }
    }
    return urls.isEmpty() ? "" : "Источники:\n" + String.join("\n", urls);
  }
}
