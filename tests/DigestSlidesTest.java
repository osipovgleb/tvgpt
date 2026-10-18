package local.tvgpt.assistant.digests;

import org.json.*;

public final class DigestSlidesTest {
  public static void main(String[] args) throws Exception {
    long now = 200000000;
    JSONArray a = new JSONArray();
    a.put(new JSONObject().put("published_at", now - 172800001).put("screensaver", true));
    a.put(
        new JSONObject()
            .put("published_at", JSONObject.NULL)
            .put("discovered_at", now - 100)
            .put("screensaver", true));
    a.put(new JSONObject().put("published_at", now).put("screensaver", false));
    a.put(
        new JSONObject()
            .put("published_at", now + 10000000)
            .put("discovered_at", now - 172800001)
            .put("screensaver", true));
    if (DigestSlides.select(a, now).length() != 1)
      throw new AssertionError("stale or disabled cards");
    if (DigestSlides.nextIndex(0, 0) != -1) throw new AssertionError("empty slides");
    JSONArray releases =
        new JSONArray()
            .put(
                new JSONObject().put("digest_id", "a").put("collected_at", 20).put("title", "new1"))
            .put(
                new JSONObject().put("digest_id", "a").put("collected_at", 20).put("title", "new2"))
            .put(new JSONObject().put("digest_id", "a").put("collected_at", 10).put("title", "old"))
            .put(
                new JSONObject()
                    .put("digest_id", "b")
                    .put("collected_at", 5)
                    .put("title", "other"));
    if (DigestSlides.latest(releases).length() != 3)
      throw new AssertionError(
          "gallery must show latest release per digest without old duplicates");
    System.out.println("DigestSlidesTest passed");
  }
}
