package local.tvgpt.assistant.digests;

import org.json.*;

public final class DigestPolicyTest {
  public static void main(String[] args) throws Exception {
    for (int[] row : new int[][] {{19, 20, 1}, {20, 20, 0}, {29, 30, 1}, {30, 30, 0}}) {
      boolean accepted = true;
      try {
        DigestPolicy.capacity(row[0], row[1]);
      } catch (IllegalArgumentException e) {
        accepted = false;
      }
      if (accepted != (row[2] == 1)) throw new AssertionError("capacity boundary");
    }
    JSONArray articles = new JSONArray();
    for (int i = 0; i < 13; i++)
      articles.put(
          new JSONObject()
              .put("title", "t" + i)
              .put("summary", "s")
              .put("url", "https://example.org/" + i));
    if (DigestPolicy.articles(articles, 100).length() != 12)
      throw new AssertionError("release limit");
    if (!DigestPolicy.articles(articles, 100).getJSONObject(0).isNull("published_at"))
      throw new AssertionError("invented date");
    try {
      DigestPolicy.kind("shell");
      throw new AssertionError("unsafe kind");
    } catch (IllegalArgumentException expected) {
    }
    System.out.println("DigestPolicyTest passed");
  }
}
