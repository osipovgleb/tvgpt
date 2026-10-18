package local.tvgpt.assistant.digests;

import java.io.*;
import org.json.*;

/** Bounded JSON protocol for subscription image generation. */
public final class SubscriptionImageProtocol {
  public static final String MODEL = "gpt-image-2";

  private SubscriptionImageProtocol() {}

  public static JSONObject request(String prompt) throws Exception {
    if (prompt == null || prompt.trim().isEmpty() || prompt.length() > 4000)
      throw new IllegalArgumentException("Некорректное описание изображения");
    return new JSONObject()
        .put("prompt", prompt)
        .put("model", MODEL)
        .put("background", "opaque")
        .put("quality", "auto")
        .put("size", "auto");
  }

  public static JSONObject read(InputStream input, int limit) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int n;
    while ((n = input.read(buffer)) != -1) {
      if ((long) bytes.size() + n > limit) throw new IOException("Image response limit");
      bytes.write(buffer, 0, n);
    }
    JSONObject response = new JSONObject(bytes.toString("UTF-8"));
    JSONObject error = response.optJSONObject("error");
    if (error != null) throw new ImageGenerationProtocol.Rejected(error.optString("code"));
    return response;
  }

  public static String image(JSONObject response) {
    JSONArray data = response == null ? null : response.optJSONArray("data");
    JSONObject first = data == null ? null : data.optJSONObject(0);
    return first == null ? "" : first.optString("b64_json");
  }
}
