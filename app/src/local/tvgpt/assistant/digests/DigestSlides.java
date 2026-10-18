package local.tvgpt.assistant.digests;

import java.util.*;
import org.json.*;

public final class DigestSlides {
  private DigestSlides() {}

  public static JSONArray select(JSONArray cards, long now) throws Exception {
    cards = latest(cards);
    JSONArray out = new JSONArray();
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < cards.length(); i++) {
      JSONObject a = cards.getJSONObject(i);
      if (!a.optBoolean("screensaver", true)) continue;
      long published = a.optLong("published_at", 0);
      if (published <= 0 || published > now + 300000) published = a.optLong("discovered_at", 0);
      if (published <= 0 || published > now + 300000 || now - published > 172800000L) continue;
      String key = a.optString("story_key", a.optString("id", ""));
      if (!key.isEmpty() && !seen.add(key)) continue;
      out.put(a);
    }
    return out;
  }

  /** Retained releases remain archived; the gallery presents the current release per digest. */
  public static JSONArray latest(JSONArray cards) throws Exception {
    Map<String, Long> newest = new HashMap<>();
    for (int i = 0; i < cards.length(); i++) {
      JSONObject a = cards.getJSONObject(i);
      String id = a.optString("digest_id");
      newest.put(id, Math.max(newest.getOrDefault(id, 0L), a.optLong("collected_at", 0)));
    }
    JSONArray out = new JSONArray();
    for (int i = 0; i < cards.length(); i++) {
      JSONObject a = cards.getJSONObject(i);
      if (a.optLong("collected_at", 0) == newest.get(a.optString("digest_id"))) out.put(a);
    }
    return out;
  }

  public static int nextIndex(int current, int count) {
    return count <= 0 ? -1 : (current + 1) % count;
  }
}
