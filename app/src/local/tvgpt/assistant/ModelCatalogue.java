package local.tvgpt.assistant;

import android.content.Context;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Subscription catalog cached for six hours; no guessed model choices or UI-thread network. */
public final class ModelCatalogue {
  private static final ExecutorService IO =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "tvgpt-models"));
  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static final List<Consumer<String>> listeners = new ArrayList<>();
  private static String cached = "{}";
  private static long loaded, retryAfter;
  private static boolean loading;

  private ModelCatalogue() {}

  public static synchronized void load(Context context, Consumer<String> result) {
    if (cached.equals("{}"))
      cached = context.getSharedPreferences("models", 0).getString("catalog", "{}");
    result.accept(cached);
    if (System.currentTimeMillis() - loaded < 6 * 60 * 60 * 1000L
        || System.currentTimeMillis() < retryAfter) return;
    listeners.add(result);
    if (loading) return;
    loading = true;
    retryAfter = System.currentTimeMillis() + 60000;
    Context app = context.getApplicationContext();
    IO.submit(
        () -> {
          String value = null;
          try {
            value = fetch(app);
          } catch (Exception e) {
            android.util.Log.w(
                "TVGPTModels", "Model catalog unavailable: " + e.getClass().getSimpleName());
          }
          synchronized (ModelCatalogue.class) {
            if (value != null) {
              cached = value;
              app.getSharedPreferences("models", 0).edit().putString("catalog", value).apply();
              loaded = System.currentTimeMillis();
            }
            loading = false;
            List<Consumer<String>> callbacks = new ArrayList<>(listeners);
            listeners.clear();
            String data = cached;
            MAIN.post(
                () -> {
                  for (Consumer<String> callback : callbacks) callback.accept(data);
                });
          }
        });
  }

  private static String fetch(Context c) throws Exception {
    HttpURLConnection connection =
        (HttpURLConnection)
            new URL("https://chatgpt.com/backend-api/codex/models?client_version=0.163.0")
                .openConnection();
    connection.setConnectTimeout(10000);
    connection.setReadTimeout(10000);
    connection.setInstanceFollowRedirects(false);
    for (Map.Entry<String, String> header :
        LiveConnection.headers(new AccountStore(c).fresh()).entrySet())
      connection.setRequestProperty(header.getKey(), header.getValue());
    try {
      if (connection.getResponseCode() != 200)
        throw new IOException("catalog HTTP " + connection.getResponseCode());
      ByteArrayOutputStream result = new ByteArrayOutputStream();
      try (InputStream in = connection.getInputStream()) {
        byte[] bytes = new byte[4096];
        int count;
        while ((count = in.read(bytes)) != -1) {
          if (result.size() + count > 1024 * 1024) throw new IOException("catalog size");
          result.write(bytes, 0, count);
        }
      }
      String json = result.toString("UTF-8");
      if (new org.json.JSONObject(json).optJSONArray("models") == null)
        throw new IOException("catalog format");
      return json;
    } finally {
      connection.disconnect();
    }
  }
}
