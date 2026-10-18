package local.tvgpt.assistant;

import android.content.Context;
import android.os.Bundle;
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;

/** Compare Hermes' subscription request shape without exporting the TV account credentials. */
final class HermesFastChecks {
  static void run(Context c, Bundle out) throws Exception {
    JSONObject account = new AccountStore(c).fresh();
    JSONObject auth =
        AccountStore.claims(account.getString("access_token"))
            .optJSONObject("https://api.openai.com/auth");
    String plan = auth == null ? "" : auth.optString("chatgpt_plan_type");
    out.putString("plan", plan.matches("[a-zA-Z0-9_-]{1,40}") ? plan : "unspecified");
    Map<String, String> common = LiveConnection.headers(account);
    common.remove("OpenAI-Alpha");
    String residency =
        auth == null
            ? ""
            : auth.optString("chatgpt_data_residency", auth.optString("chatgpt_compute_residency"));
    if (residency.matches("[a-zA-Z0-9_-]{1,48}"))
      common.put("x-openai-internal-codex-residency", residency);
    out.putBoolean("residency_header", common.containsKey("x-openai-internal-codex-residency"));
    String model = ModelSettings.discussion(c);
    JSONObject catalog = catalog(common);
    JSONArray models = catalog.optJSONArray("models");
    for (int i = 0; models != null && i < models.length(); i++) {
      JSONObject entry = models.getJSONObject(i);
      if (model.equals(entry.optString("slug")))
        out.putString("fresh_catalog_tiers", String.valueOf(entry.opt("service_tiers")));
    }
    for (boolean fast : new boolean[] {false, true}) {
      Map<String, String> headers = new LinkedHashMap<>(common);
      String id = UUID.randomUUID().toString(),
          cache = "pck_" + id.replace("-", "").substring(0, 24);
      headers.put("session_id", id);
      headers.put("x-client-request-id", cache);
      JSONObject request =
          new JSONObject()
              .put("model", model)
              .put("instructions", "Краткий ответ по-русски.")
              .put(
                  "input",
                  new JSONArray()
                      .put(
                          new JSONObject()
                              .put("role", "user")
                              .put(
                                  "content",
                                  new JSONArray()
                                      .put(
                                          new JSONObject()
                                              .put("type", "input_text")
                                              .put(
                                                  "text",
                                                  "Сколько будет два плюс два? Одним словом.")))))
              .put("store", false)
              .put("stream", true)
              .put("prompt_cache_key", cache)
              .put("reasoning", new JSONObject().put("effort", "low").put("summary", "auto"))
              .put("include", new JSONArray().put("reasoning.encrypted_content"));
      if (fast) request.put("service_tier", "priority");
      Http.Reply reply =
          Http.post(
              "https://chatgpt.com/backend-api/codex/responses",
              request.toString(),
              "application/json",
              headers);
      String prefix = fast ? "hermes_fast_" : "hermes_standard_";
      out.putInt(prefix + "http", reply.code);
      if (reply.code == 200) {
        JSONObject completed = LiveProtocol.completedResponse(reply.body);
        out.putString(
            prefix + "served",
            completed == null ? "incomplete" : completed.optString("service_tier", "unspecified"));
      } else
        out.putString(
            prefix + "error", reply.body.substring(0, Math.min(300, reply.body.length())));
    }
  }

  private static JSONObject catalog(Map<String, String> headers) throws Exception {
    HttpURLConnection connection =
        (HttpURLConnection)
            new URL("https://chatgpt.com/backend-api/codex/models?client_version=0.163.0")
                .openConnection();
    connection.setConnectTimeout(10000);
    connection.setReadTimeout(10000);
    connection.setInstanceFollowRedirects(false);
    for (Map.Entry<String, String> h : headers.entrySet())
      connection.setRequestProperty(h.getKey(), h.getValue());
    try {
      if (connection.getResponseCode() != 200)
        throw new IOException("Catalog HTTP " + connection.getResponseCode());
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (InputStream in = connection.getInputStream()) {
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) {
          if (bytes.size() + n > 1024 * 1024) throw new IOException("Catalog size");
          bytes.write(buffer, 0, n);
        }
      }
      return new JSONObject(bytes.toString("UTF-8"));
    } finally {
      connection.disconnect();
    }
  }
}
