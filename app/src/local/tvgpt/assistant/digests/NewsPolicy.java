package local.tvgpt.assistant.digests;

import java.net.*;
import java.util.*;
import org.json.*;

public final class NewsPolicy {
  private NewsPolicy() {}

  public static String normalizeUrl(String raw) throws Exception {
    URI uri = new URI(raw);
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || (uri.getPort() != -1 && uri.getPort() != 443))
      throw new IllegalArgumentException("Требуется публичный HTTPS адрес");
    String query = uri.getRawQuery();
    List<String> fields = new ArrayList<>();
    if (query != null)
      for (String part : query.split("&")) {
        String key = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
        if (!key.startsWith("utm_") && !Arrays.asList("fbclid", "gclid", "yclid").contains(key))
          fields.add(part);
      }
    return "https://"
        + uri.getHost().toLowerCase(Locale.ROOT)
        + (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
        + (fields.isEmpty() ? "" : "?" + String.join("&", fields));
  }

  public static boolean publicAddress(InetAddress ip) {
    byte[] b = ip.getAddress();
    return !ip.isAnyLocalAddress()
        && !ip.isLoopbackAddress()
        && !ip.isLinkLocalAddress()
        && !ip.isSiteLocalAddress()
        && !ip.isMulticastAddress()
        && !(b.length == 16 && (b[0] & 254) == 252)
        && !(b.length == 4
            && ((b[0] & 255) == 0
                || ((b[0] & 255) == 100 && (b[1] & 255) >= 64 && (b[1] & 255) <= 127)));
  }

  public static JSONArray select(JSONArray candidates, long now) throws Exception {
    JSONArray out = new JSONArray();
    Set<String> urls = new HashSet<>();
    Map<String, JSONObject> stories = new LinkedHashMap<>();
    for (int i = 0; i < Math.min(30, candidates.length()) && out.length() < 12; i++) {
      JSONObject a = new JSONObject(candidates.getJSONObject(i).toString());
      String url;
      try {
        url = normalizeUrl(a.getString("url"));
      } catch (Exception e) {
        continue;
      }
      if (!urls.add(url)) continue;
      long date = a.optLong("published_at", 0);
      if (date > 0 && date <= now + 300000 && now - date > 172800000L) continue;
      if (date <= 0 || date > now + 300000) a.put("published_at", JSONObject.NULL);
      a.put("url", url);
      String key = a.optString("story_key").trim();
      if (key.isEmpty()) key = url;
      a.put("story_key", key);
      JSONObject prior = stories.get(key);
      if (prior != null) {
        JSONArray sources = prior.optJSONArray("sources");
        if (sources == null) {
          sources = new JSONArray();
          prior.put("sources", sources);
        }
        JSONArray extra = a.optJSONArray("sources");
        if (extra != null)
          for (int j = 0; j < extra.length() && sources.length() < 8; j++)
            sources.put(extra.get(j));
        continue;
      }
      stories.put(key, a);
      out.put(a);
    }
    return out;
  }
}
