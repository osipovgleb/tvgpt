package local.tvgpt.assistant.digests;

import java.util.*;
import org.json.*;

public final class DigestPolicy {
  private DigestPolicy() {}

  public static void capacity(int count, int max) {
    if (count >= max) throw new IllegalArgumentException("Достигнут лимит");
  }

  public static String kind(String kind) {
    if (!Arrays.asList("digest", "source", "schedule").contains(kind))
      throw new IllegalArgumentException("Неизвестная операция");
    return kind;
  }

  public static String id(String id) {
    UUID.fromString(id);
    return id;
  }

  public static String text(JSONObject data, String key, int max) {
    String value = data.optString(key, "").trim();
    if (value.length() > max) throw new IllegalArgumentException("Слишком длинное поле: " + key);
    return value;
  }

  public static JSONArray articles(JSONArray input, long now) throws Exception {
    JSONArray result = new JSONArray();
    for (int i = 0; i < Math.min(12, input.length()); i++) {
      JSONObject a = new JSONObject(input.getJSONObject(i).toString());
      a.put("title", text(a, "title", 120));
      a.put("summary", text(a, "summary", 800));
      if (a.optString("title").isEmpty()) continue;
      if (!a.has("id")) a.put("id", UUID.randomUUID().toString());
      if (!a.has("published_at")) a.put("published_at", JSONObject.NULL);
      a.put("discovered_at", now);
      result.put(a);
    }
    return result;
  }
}
