package local.tvgpt.assistant.digests;

import java.nio.charset.StandardCharsets;
import java.security.*;

public final class DigestRunPolicy {
  private DigestRunPolicy() {}

  public static String generationKey(org.json.JSONObject article, String style) throws Exception {
    return coverKey(NewsPolicy.normalizeUrl(article.getString("url")), style);
  }

  public static String result(boolean coverOnly, String generationState, boolean allCovered) {
    if (coverOnly && !generationState.equals("ready"))
      return generationState.equals("unconfirmed") ? "unconfirmed" : "failed";
    return allCovered ? "ready" : "partial";
  }

  public static String coverKey(String story, String style) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest((story + "\n" + style).getBytes(StandardCharsets.UTF_8));
      StringBuilder out = new StringBuilder();
      for (byte b : digest) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
      return out.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
