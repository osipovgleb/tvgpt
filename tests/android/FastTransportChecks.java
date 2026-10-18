package local.tvgpt.assistant;

import android.content.Context;
import android.os.Bundle;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;
import org.json.*;

/** Synthetic subscription probes; account credentials never leave the TV or canonical endpoint. */
final class FastTransportChecks {
  static void run(Context context, Bundle result) throws Exception {
    run(context, result, false);
  }

  static void benchmark(Context context, Bundle result) throws Exception {
    run(context, result, true);
  }

  private static void run(Context context, Bundle result, boolean production) throws Exception {
    JSONObject account = new AccountStore(context).fresh();
    Map<String, String> headers = LiveConnection.headers(account);
    if (!production) headers.remove("OpenAI-Alpha");
    JSONObject claims =
        AccountStore.claims(account.getString("access_token"))
            .optJSONObject("https://api.openai.com/auth");
    String residency =
        claims == null
            ? ""
            : claims.optString(
                "chatgpt_data_residency", claims.optString("chatgpt_compute_residency"));
    if (!production && residency.matches("[a-zA-Z0-9_-]{1,48}"))
      headers.put("x-openai-internal-codex-residency", residency);
    String model = ModelSettings.discussion(context);
    OkHttpClient client =
        new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .callTimeout(55, TimeUnit.SECONDS)
            .readTimeout(50, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();
    JSONArray comparisons = new JSONArray();
    try {
      for (boolean fast : new boolean[] {false, true, true, false}) {
        Probe probe = new Probe(production ? 300 : 100);
        JSONObject body = request(model, fast, false);
        if (production) {
          body.put("service_tier", fast ? "priority" : "default");
          body.getJSONArray("input")
              .getJSONObject(0)
              .put(
                  "content",
                  "Output exactly the integers 0001 through 0300 in ascending order, separated by"
                      + " single spaces. No prose, no code fence, no omissions.");
        }
        Request.Builder request =
            builder(headers, model, fast)
                .url("https://chatgpt.com/backend-api/codex/responses")
                .post(RequestBody.create(MediaType.parse("application/json"), body.toString()));
        try (Response response = client.newCall(request.build()).execute()) {
          probe.http = response.code();
          probe.headers(response.headers());
          if (response.isSuccessful()) {
            String line;
            while ((line = response.body().source().readUtf8Line()) != null) {
              if (line.startsWith("data:") && !line.substring(5).trim().equals("[DONE]"))
                probe.event(new JSONObject(line.substring(5).trim()));
              if (probe.terminal) break;
            }
          }
        }
        comparisons.put(
            probe
                .report(fast)
                .put("requested", fast ? "priority" : production ? "default" : "omitted"));
        android.util.Log.i(
            "TVGPTFast", "Synthetic comparison " + comparisons.length() + " complete");
        result.putString("http_comparison", comparisons.toString());
      }
      if (production) return;
      Probe websocket = new Probe(100);
      CountDownLatch done = new CountDownLatch(1);
      Request request =
          builder(headers, model, true)
              .url("wss://chatgpt.com/backend-api/codex/responses")
              .header("OpenAI-Beta", "responses_websockets=2026-02-06")
              .build();
      WebSocket socket =
          client.newWebSocket(
              request,
              new WebSocketListener() {
                public void onOpen(WebSocket ws, Response response) {
                  websocket.http = response.code();
                  websocket.headers(response.headers());
                  try {
                    ws.send(request(model, true, true).put("type", "response.create").toString());
                  } catch (Exception failure) {
                    websocket.error = failure.getClass().getSimpleName();
                    done.countDown();
                  }
                }

                public void onMessage(WebSocket ws, String text) {
                  try {
                    websocket.event(new JSONObject(text));
                  } catch (Exception failure) {
                    websocket.error = failure.getClass().getSimpleName();
                    done.countDown();
                  }
                  if (websocket.terminal) done.countDown();
                }

                public void onFailure(WebSocket ws, Throwable failure, Response response) {
                  websocket.http = response == null ? 0 : response.code();
                  websocket.error = failure.getClass().getSimpleName();
                  done.countDown();
                }

                public void onClosed(WebSocket ws, int code, String reason) {
                  done.countDown();
                }
              });
      try {
        if (!done.await(55, TimeUnit.SECONDS)) websocket.error = "timeout";
        result.putString("websocket_fast", websocket.report(true).toString());
      } finally {
        socket.cancel();
      }
    } finally {
      client.dispatcher().executorService().shutdown();
      client.connectionPool().evictAll();
    }
  }

  private static Request.Builder builder(Map<String, String> headers, String model, boolean fast) {
    Request.Builder builder = new Request.Builder();
    for (Map.Entry<String, String> header : headers.entrySet())
      builder.header(header.getKey(), header.getValue());
    String id = UUID.randomUUID().toString();
    return builder
        .header("session-id", id)
        .header("thread-id", id)
        .header("x-client-request-id", id)
        .header("x-codex-routing-hint", CodexRouting.hint(model, fast ? "priority" : "default"))
        .header("x-responsesapi-include-timing-metrics", "true")
        .header("User-Agent", "tvgpt/0.1 Android");
  }

  private static JSONObject request(String model, boolean fast, boolean shortAnswer)
      throws Exception {
    JSONObject request =
        new JSONObject()
            .put("model", model)
            .put("store", false)
            .put("stream", true)
            .put("instructions", "Follow the requested output format exactly. Do not call tools.")
            .put(
                "input",
                new JSONArray()
                    .put(
                        new JSONObject()
                            .put("role", "user")
                            .put(
                                "content",
                                shortAnswer
                                    ? "Reply exactly OK."
                                    : "Output exactly the integers 0001 through 0100 in ascending"
                                        + " order, separated by single spaces. No prose, no code"
                                        + " fence, no omissions.")))
            .put("reasoning", new JSONObject().put("effort", "low"))
            .put("include", new JSONArray().put("reasoning.encrypted_content"));
    if (fast) request.put("service_tier", "priority");
    return request;
  }

  private static final class Probe {
    final int expectedCount;

    Probe(int expectedCount) {
      this.expectedCount = expectedCount;
    }

    final long start = System.nanoTime();
    long first, last;
    int http, outputTokens, reasoningTokens, bytes;
    String created = "", completed = "", error = "";
    boolean terminal;
    final StringBuilder output = new StringBuilder();
    final JSONObject tierHeaders = new JSONObject();

    void headers(Headers headers) {
      for (String name : headers.names()) {
        if (name.toLowerCase(Locale.ROOT).contains("tier")) {
          String value = headers.get(name);
          if (value != null && value.matches("[a-zA-Z0-9_;= .-]{1,100}"))
            try {
              tierHeaders.put(name, value);
            } catch (Exception ignored) {
            }
        }
      }
    }

    void event(JSONObject event) throws Exception {
      bytes += event.toString().length();
      if (bytes > 1024 * 1024) throw new Exception("Probe response limit");
      String type = event.optString("type");
      JSONObject response = event.optJSONObject("response");
      if (type.equals("response.created") && response != null)
        created = response.optString("service_tier");
      if (type.equals("response.output_text.delta")) {
        if (first == 0) first = System.nanoTime();
        last = System.nanoTime();
        output.append(event.optString("delta"));
      }
      if (type.equals("error") || type.equals("response.failed")) {
        JSONObject problem = event.optJSONObject("error");
        error = problem == null ? type : problem.optString("code", type);
        terminal = true;
      }
      if (type.equals("response.completed") && response != null) {
        completed = response.optString("service_tier");
        terminal = true;
        JSONObject usage = response.optJSONObject("usage");
        if (usage != null) {
          outputTokens = usage.optInt("output_tokens");
          JSONObject details = usage.optJSONObject("output_tokens_details");
          reasoningTokens = details == null ? 0 : details.optInt("reasoning_tokens");
        }
      }
    }

    JSONObject report(boolean fast) throws Exception {
      String text = output.toString().trim();
      StringBuilder expected = new StringBuilder();
      for (int i = 1; i <= expectedCount; i++) {
        if (i > 1) expected.append(' ');
        expected.append(String.format(Locale.ROOT, "%04d", i));
      }
      return new JSONObject()
          .put("requested", fast ? "priority" : "omitted")
          .put("http", http)
          .put("created", created)
          .put("completed", completed)
          .put("terminal", terminal)
          .put("error", error)
          .put("tier_headers", tierHeaders)
          .put("elapsed_ms", (System.nanoTime() - start) / 1000000)
          .put("first_output_ms", first == 0 ? -1 : (first - start) / 1000000)
          .put("generation_ms", first == 0 ? -1 : (last - first) / 1000000)
          .put("output_tokens", outputTokens)
          .put("reasoning_tokens", reasoningTokens)
          .put("matched_output", text.equals(expected.toString()))
          .put("short_ok", text.equals("OK"));
    }
  }
}
