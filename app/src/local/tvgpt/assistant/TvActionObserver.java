package local.tvgpt.assistant;

import android.content.*;
import android.media.AudioManager;
import android.os.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Runs commands off-main and returns a fresh observation with an explicit confirmation scope. */
final class TvActionObserver {
  private final Context context;
  private final Handler main = new Handler(Looper.getMainLooper());

  TvActionObserver(Context context) {
    this.context = context;
  }

  String execute(String name, JSONObject args, Callable<String> action) throws Exception {
    if (name.equals("wait_for_screen")) return waitForScreen(args);
    if (!mutates(name)) return action.call();
    if (Looper.myLooper() == Looper.getMainLooper())
      throw new IllegalStateException("Actions with observation require a worker thread");
    JSONObject before = snapshot();
    int expectedVolume = expectedVolume(name, args);
    String receipt = action.call();
    if (receipt.equals("Приложение не найдено")
        || receipt.startsWith("У приложения нет")
        || receipt.startsWith("Не ")
        || receipt.startsWith("Нуж")
        || receipt.startsWith("Пульт недоступен")
        || receipt.contains("ввод отменён")
        || receipt.startsWith("Доступный элемент не найден"))
      return ActionObservation.result(receipt, snapshot(), "").put("status", "rejected").toString();
    try {
      return observe(name, args, receipt, before, expectedVolume);
    } catch (InterruptedException cancelled) {
      Thread.currentThread().interrupt();
      throw cancelled;
    } catch (Exception unavailable) {
      android.util.Log.w(
          "TVGPTVerify",
          "Post-action observation unavailable: " + unavailable.getClass().getSimpleName());
      return ActionObservation.result(receipt, new JSONObject(), "")
          .put("requires_visual", !name.equals("volume"))
          .put("observation_error", unavailable.getClass().getSimpleName())
          .toString();
    }
  }

  private String observe(
      String name, JSONObject args, String receipt, JSONObject before, int expectedVolume)
      throws Exception {
    String target = targetPackage(name, args);
    final JSONObject[] latest = {snapshot()};
    String scope = "";
    long start = ActionWait.MONOTONIC.now();
    if (name.equals("volume")) {
      boolean matched =
          ActionWait.until(
              () -> {
                latest[0] = snapshot();
                return args.has("muted")
                    ? latest[0].optBoolean("muted") == args.getBoolean("muted")
                    : latest[0].optInt("volume", -1) == expectedVolume;
              },
              1200,
              ActionWait.MONOTONIC);
      if (matched) scope = args.has("muted") ? "mute_readback" : "volume_readback";
    } else if (!target.isEmpty()) {
      boolean matched =
          ActionWait.until(
              () -> {
                latest[0] = snapshot();
                return ActionObservation.samePackage(latest[0], target);
              },
              5000,
              ActionWait.MONOTONIC);
      if (matched) scope = "app_visible";
    } else {
      boolean changed =
          ActionWait.until(
              () -> {
                latest[0] = snapshot();
                // A virtual Cobalt tree cannot confirm content or focus. Don't poll it pointlessly.
                return latest[0].optString("text").isEmpty()
                    || ActionObservation.changed(before, latest[0]);
              },
              1200,
              ActionWait.MONOTONIC);
      if (changed && ActionObservation.changed(before, latest[0])) scope = "screen_changed";
    }
    boolean contentGoal =
        name.equals("search_app")
            || name.equals("navigate_sequence")
            || name.equals("click")
            || name.equals("tap")
            || name.equals("type_text")
            || (name.equals("navigate")
                && (args.optString("direction").equals("select")
                    || args.optString("direction").equals("back")));
    JSONObject result = ActionObservation.result(receipt, latest[0], scope);
    if (contentGoal) {
      result.put("goal_status", "requires_content_check");
      if (latest[0].optString("text").trim().isEmpty()) result.put("requires_visual", true);
      if (name.equals("search_app")) result.put("requested_query", args.optString("query"));
    }
    result.put("observation_ms", ActionWait.MONOTONIC.now() - start);
    android.util.Log.i(
        "TVGPTVerify",
        name + " scope=" + scope + " visual=" + result.optBoolean("requires_visual"));
    return result.toString();
  }

  private String waitForScreen(JSONObject args) throws Exception {
    if (Looper.myLooper() == Looper.getMainLooper())
      throw new IllegalStateException("worker required");
    String target = args.optString("package"), text = args.optString("contains");
    if (target.isEmpty() && text.isEmpty()) return "Укажи ожидаемое приложение или текст";
    long budget = Math.max(0, Math.min(5000, args.optInt("timeout_ms", 3000)));
    JSONObject[] latest = {snapshot()};
    boolean[] visual = {false};
    boolean observed =
        ActionWait.until(
            () -> {
              latest[0] = snapshot();
              if (!target.isEmpty() && !ActionObservation.samePackage(latest[0], target))
                return false;
              if (text.isEmpty()) return true;
              if (latest[0].optString("text").isEmpty()) {
                visual[0] = true;
                return true;
              }
              return latest[0]
                  .optString("text")
                  .toLowerCase(java.util.Locale.ROOT)
                  .contains(text.toLowerCase(java.util.Locale.ROOT));
            },
            budget,
            ActionWait.MONOTONIC);
    return ActionObservation.result(
            "Проверка ожидаемого состояния",
            latest[0],
            observed && !visual[0] ? (text.isEmpty() ? "app_visible" : "text_visible") : "")
        .put("requires_visual", visual[0])
        .toString();
  }

  private int expectedVolume(String name, JSONObject args) {
    if (!name.equals("volume") || args.has("muted")) return -1;
    AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    if (args.has("percent"))
      return Math.round(max * Math.max(0, Math.min(100, args.optInt("percent"))) / 100f);
    return Math.max(
        0,
        Math.min(
            max,
            audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                + (args.optString("direction").equals("down") ? -1 : 1)));
  }

  private String targetPackage(String name, JSONObject args) throws Exception {
    if (name.equals("open_app") || name.equals("search_app")) {
      String query = args.optString("name").trim();
      if (name.equals("search_app")
          && (query.isEmpty()
              || query.equalsIgnoreCase("current")
              || query.equalsIgnoreCase("текущее приложение")))
        return snapshot().optString("package");
      String found = TvTools.resolveApp(context.getPackageManager(), query);
      return found == null ? "" : found;
    }
    if (name.equals("navigate") && args.optString("direction").equals("home")) {
      android.content.pm.ResolveInfo home =
          context
              .getPackageManager()
              .resolveActivity(
                  new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                  android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
      return home == null ? "" : home.activityInfo.packageName;
    }
    return "";
  }

  private JSONObject snapshot() throws Exception {
    FutureTask<JSONObject> task =
        new FutureTask<>(
            () -> {
              JSONObject state =
                  TvAccessibility.current == null
                      ? new JSONObject()
                      : TvAccessibility.current.observation();
              AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
              return state
                  .put("volume", audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                  .put("muted", audio.isStreamMute(AudioManager.STREAM_MUSIC));
            });
    main.post(task);
    try {
      return task.get(2, TimeUnit.SECONDS);
    } catch (Exception e) {
      task.cancel(false);
      throw e;
    }
  }

  private static boolean mutates(String name) {
    return name.equals("open_app")
        || name.equals("search_app")
        || name.equals("navigate")
        || name.equals("navigate_sequence")
        || name.equals("volume")
        || name.equals("click")
        || name.equals("tap")
        || name.equals("type_text");
  }
}
