package local.tvgpt.assistant.digests;

import java.net.*;
import org.json.*;

public final class NewsPolicyTest {
  public static void main(String[] x) throws Exception {
    if (!NewsPolicy.normalizeUrl("https://example.org/news?id=1&utm_source=x#here")
        .equals("https://example.org/news?id=1")) throw new AssertionError("lost real query");
    for (String ip : new String[] {"127.0.0.1", "192.168.1.1", "169.254.169.254", "::1", "fc00::1"})
      if (NewsPolicy.publicAddress(InetAddress.getByName(ip)))
        throw new AssertionError("private network");
    JSONArray a =
        new JSONArray()
            .put(
                new JSONObject()
                    .put("url", "https://example.org/1")
                    .put("title", "one")
                    .put("published_at", 1))
            .put(new JSONObject().put("url", "https://example.org/2").put("title", "two"));
    JSONArray selected = NewsPolicy.select(a, 200000000);
    if (selected.length() != 1 || !selected.getJSONObject(0).isNull("published_at"))
      throw new AssertionError("date provenance");
    JSONArray duplicate =
        new JSONArray()
            .put(
                new JSONObject()
                    .put("url", "https://a.example/news")
                    .put("story_key", "same")
                    .put(
                        "sources",
                        new JSONArray().put(new JSONObject().put("url", "https://a.example/news"))))
            .put(
                new JSONObject()
                    .put("url", "https://b.example/news")
                    .put("story_key", "same")
                    .put(
                        "sources",
                        new JSONArray()
                            .put(new JSONObject().put("url", "https://b.example/news"))));
    JSONArray merged = NewsPolicy.select(duplicate, 200000000);
    if (merged.length() != 1 || merged.getJSONObject(0).getJSONArray("sources").length() != 2)
      throw new AssertionError("duplicate story lost sources");
    System.out.println("NewsPolicyTest passed");
  }
}
