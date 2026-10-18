package local.tvgpt.assistant.digests;

public final class DigestRunPolicyTest {
  public static void main(String[] a) throws Exception {
    if (!DigestRunPolicy.coverKey("story", "style")
        .equals(DigestRunPolicy.coverKey("story", "style")))
      throw new AssertionError("unstable cache");
    if (DigestRunPolicy.coverKey("story", "style")
        .equals(DigestRunPolicy.coverKey("story2", "style")))
      throw new AssertionError("wrong story");
    if (!DigestRunPolicy.result(true, "failed", true).equals("failed")
        || !DigestRunPolicy.result(true, "unconfirmed", true).equals("unconfirmed")
        || !DigestRunPolicy.result(false, "failed", true).equals("ready"))
      throw new AssertionError("generation failure hidden by photo");
    org.json.JSONObject first =
        new org.json.JSONObject().put("url", "https://example.org/story").put("story_key", "a");
    org.json.JSONObject second =
        new org.json.JSONObject().put("url", "https://example.org/story").put("story_key", "b");
    if (!DigestRunPolicy.generationKey(first, "style")
        .equals(DigestRunPolicy.generationKey(second, "style")))
      throw new AssertionError("model key bypasses reservation");
    System.out.println("DigestRunPolicyTest passed");
  }
}
