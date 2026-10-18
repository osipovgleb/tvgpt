package local.tvgpt.assistant.digests;

import org.json.*;

public final class DigestResearchProtocol {
  private DigestResearchProtocol() {}

  public static JSONArray parse(JSONObject response) throws Exception {
    StringBuilder text = new StringBuilder();
    JSONArray output = response.optJSONArray("output");
    if (output != null)
      for (int i = 0; i < output.length(); i++) {
        JSONObject item = output.getJSONObject(i);
        if (item.optString("type").equals("function_call"))
          throw new IllegalArgumentException("Недопустимый инструмент");
        JSONArray content = item.optJSONArray("content");
        if (content != null)
          for (int j = 0; j < content.length(); j++) {
            JSONObject part = content.getJSONObject(j);
            if (part.optString("type").equals("output_text")) text.append(part.optString("text"));
          }
      }
    String raw =
        text.toString().trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
    if (raw.length() > 48000) throw new IllegalArgumentException("Слишком большой выпуск");
    return new JSONArray(raw);
  }
}
