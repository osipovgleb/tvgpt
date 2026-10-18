package local.tvgpt.assistant;

import android.content.Context;
import android.os.Bundle;
import java.util.*;
import org.json.*;

/** Auth stays on-device. Report only response routing metadata for known Codex request variants. */
final class FastWireChecks {
  static void run(Context context, Bundle result) throws Exception {
    for (String variant :
        new String[] {"fast_alias", "codex_headers", "responses_lite", "responses_lite_standard"}) {
      String tier =
          variant.equals("fast_alias")
              ? "fast"
              : variant.equals("responses_lite_standard") ? "default" : "priority";
      Map<String, String> headers = LiveConnection.headers(new AccountStore(context).fresh());
      headers.remove("OpenAI-Alpha");
      headers.put(
          "x-codex-routing-hint", "model=" + ModelSettings.discussion(context) + ";tier=" + tier);
      if (!variant.equals("fast_alias")) {
        headers.put("originator", "codex_cli_rs");
        headers.put("version", "0.163.0");
        headers.put("User-Agent", "codex_cli_rs/0.163.0");
      }
      if (variant.startsWith("responses_lite"))
        headers.put("x-openai-internal-codex-responses-lite", "true");
      JSONArray input =
          new JSONArray()
              .put(
                  new JSONObject()
                      .put("role", "user")
                      .put("content", "Сколько будет два плюс два? Ответь одним словом."));
      JSONObject request =
          new JSONObject()
              .put("model", ModelSettings.discussion(context))
              .put("stream", true)
              .put("service_tier", tier)
              .put("store", false)
              .put("input", input)
              .put("tool_choice", "auto")
              .put("parallel_tool_calls", false)
              .put("include", new JSONArray())
              .put("reasoning", new JSONObject().put("effort", "low"));
      if (!variant.startsWith("responses_lite"))
        request.put("instructions", "Краткий ответ по-русски.");
      if (variant.startsWith("responses_lite"))
        request.getJSONObject("reasoning").put("context", "all_turns");
      Http.Reply reply =
          Http.post(
              "https://chatgpt.com/backend-api/codex/responses",
              request.toString(),
              "application/json",
              headers);
      result.putInt(variant + "_http", reply.code);
      if (reply.code != 200) {
        String message = reply.body;
        try {
          JSONObject error = new JSONObject(reply.body).optJSONObject("error");
          if (error != null) message = error.optString("message", error.optString("code"));
        } catch (Exception ignored) {
        }
        result.putString(variant + "_error", message.substring(0, Math.min(450, message.length())));
      }
      if (reply.code == 200) {
        JSONObject completed = LiveProtocol.completedResponse(reply.body);
        result.putString(
            variant + "_served",
            completed == null ? "incomplete" : completed.optString("service_tier", "unspecified"));
      }
    }
  }
}
