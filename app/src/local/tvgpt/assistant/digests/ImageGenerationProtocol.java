package local.tvgpt.assistant.digests;

import java.io.*;
import java.util.*;
import org.json.*;

/** Completion is required; output-item events alone never prove success. */
public final class ImageGenerationProtocol {
  private ImageGenerationProtocol() {}

  /**
   * An explicit server rejection is different from an interrupted stream. No response text is
   * logged.
   */
  public static final class Rejected extends IOException {
    public final String code;

    Rejected(String raw) {
      super("Генерация отклонена: " + safeCode(raw));
      code = safeCode(raw);
    }
  }

  private static String safeCode(String raw) {
    return raw != null && raw.matches("[A-Za-z0-9_.:-]{1,96}") ? raw : "generation_rejected";
  }

  private static Rejected rejection(JSONObject event) {
    JSONObject response = event.optJSONObject("response");
    JSONObject error = response == null ? null : response.optJSONObject("error");
    if (error == null) error = event.optJSONObject("error");
    return new Rejected(error == null ? event.optString("code") : error.optString("code"));
  }

  public static JSONObject request(String model, String prompt) throws Exception {
    if (prompt == null || prompt.trim().isEmpty() || prompt.length() > 4000)
      throw new IllegalArgumentException("Некорректное описание изображения");
    return new JSONObject()
        .put("model", model)
        .put("store", false)
        .put("stream", true)
        .put("instructions", "Создай иллюстрацию по описанию. Без текста и заголовков на картинке.")
        .put(
            "input",
            new JSONArray().put(new JSONObject().put("role", "user").put("content", prompt)))
        .put("tools", new JSONArray().put(new JSONObject().put("type", "image_generation")))
        .put("tool_choice", new JSONObject().put("type", "image_generation"));
  }

  public static JSONObject completed(String sse) throws Exception {
    return read(new ByteArrayInputStream(sse.getBytes("UTF-8")), 32 * 1024 * 1024);
  }

  public static JSONObject read(InputStream stream, int limit) throws Exception {
    InputStream bounded =
        new java.io.FilterInputStream(stream) {
          private int count;

          public int read() throws IOException {
            int value = super.read();
            if (value != -1 && ++count > limit) throw new IOException("Image response limit");
            return value;
          }

          public int read(byte[] b, int offset, int length) throws IOException {
            int n = in.read(b, offset, Math.min(length, Math.max(1, limit - count + 1)));
            if (n > 0 && (count += n) > limit) throw new IOException("Image response limit");
            return n;
          }
        };
    SortedMap<Integer, JSONObject> items = new TreeMap<>();
    BufferedReader reader = new BufferedReader(new InputStreamReader(bounded, "UTF-8"));
    String line;
    while ((line = reader.readLine()) != null) {
      if (!line.startsWith("data:")) continue;
      String raw = line.substring(5).trim();
      if (raw.isEmpty() || raw.equals("[DONE]")) continue;
      JSONObject event = new JSONObject(raw);
      String type = event.optString("type");
      if (type.equals("response.failed") || type.equals("error")) throw rejection(event);
      if (type.equals("response.output_item.done")) {
        int index = event.optInt("output_index", -1);
        JSONObject item = event.optJSONObject("item");
        if (index >= 0 && index < 256 && item != null) items.put(index, item);
      }
      if (type.equals("response.completed")) {
        JSONObject result = event.getJSONObject("response");
        if (result.optString("status").equals("failed")) throw rejection(event);
        if (!result.optString("status", "completed").equals("completed")) return null;
        JSONArray output = result.optJSONArray("output");
        if (output == null || output.length() == 0) {
          output = new JSONArray();
          for (JSONObject item : items.values()) output.put(item);
          result.put("output", output);
        }
        return result;
      }
    }
    return null;
  }

  public static String image(JSONObject response) {
    if (response == null) return "";
    JSONArray items = response.optJSONArray("output");
    if (items != null)
      for (int i = 0; i < items.length(); i++) {
        JSONObject item = items.optJSONObject(i);
        if (item != null
            && item.optString("type").equals("image_generation_call")
            && item.optString("status").equals("completed")) return item.optString("result");
      }
    return "";
  }
}
