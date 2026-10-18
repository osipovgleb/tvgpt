package local.tvgpt.assistant;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import org.json.JSONObject;

/** Real background playback checks; never infer success from sending a key or opening an app. */
final class BackgroundMediaChecks {
  static void run(Instrumentation instrumentation, Bundle out) throws Exception {
    Context context = instrumentation.getTargetContext();
    context.startActivity(
        new Intent(context, MainActivity.class)
            .putExtra("section", "SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    if (!ActionWait.until(() -> TvAccessibility.current != null, 20000, ActionWait.MONOTONIC))
      throw new Exception("Accessibility not bound");
    TvTools tools = new TvTools(context);
    String foreground =
        new JSONObject(tools.execute("read_screen", new JSONObject())).optString("package");
    if (foreground.equals("com.spotify.tv.android"))
      throw new Exception("Test requires Spotify in background");
    try {
      for (String action : new String[] {"play", "pause"}) {
        JSONObject result =
            new JSONObject(
                tools.execute(
                    "media_control",
                    new JSONObject().put("action", action).put("name", "com.spotify.tv.android")));
        String scope = action.equals("play") ? "playback_active" : "playback_paused";
        if (!result.optString("status").equals("observed")
            || !result.optString("confirmed_scope").equals(scope)
            || !result
                .getJSONObject("observation")
                .optString("package")
                .equals("com.spotify.tv.android"))
          throw new Exception("Unconfirmed background " + action + ": " + result);
        String after =
            new JSONObject(tools.execute("read_screen", new JSONObject())).optString("package");
        if (!foreground.equals(after))
          throw new Exception("Background action changed foreground app");
        out.putBoolean("background_" + action + "_observed", true);
      }
      out.putBoolean("foreground_preserved", true);
      JSONObject repeated =
          new JSONObject(
              tools.execute(
                  "media_control", new JSONObject().put("action", "pause").put("name", "Spotify")));
      if (!repeated.optString("receipt").equals("Состояние уже установлено"))
        throw new Exception("Already paused action was dispatched again");
      out.putBoolean("already_paused_not_repeated", true);
    } finally {
      // A diagnostic must not leave test music playing.
      tools.execute(
          "media_control",
          new JSONObject().put("action", "pause").put("name", "com.spotify.tv.android"));
    }
  }
}
