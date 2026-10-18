package local.tvgpt.assistant;

import android.content.Context;
import local.tvgpt.assistant.settings.ModelOptions;

public final class ModelSettings {
  private ModelSettings() {}

  public static String discussion(Context c) {
    return read(c, "discussion", "gpt-6.1-sol");
  }

  /** Copy the existing choice once; future discussion changes cannot move digest work. */
  public static synchronized String digests(Context c) {
    android.content.SharedPreferences prefs = c.getSharedPreferences("models", 0);
    if (!prefs.contains("digests")) prefs.edit().putString("digests", discussion(c)).commit();
    return read(c, "digests", "gpt-6.1-sol");
  }

  public static String summary(Context c) {
    return read(c, "summary", "gpt-6.1-sol");
  }

  public static String live(Context c) {
    return read(c, "live", "gpt-live-1-codex");
  }

  public static boolean fast(Context c) {
    try {
      return c.getSharedPreferences("models", 0).getBoolean("fast", false);
    } catch (ClassCastException e) {
      return false;
    }
  }

  public static void fast(Context c, boolean enabled) {
    c.getSharedPreferences("models", 0).edit().putBoolean("fast", enabled).apply();
  }

  public static String fastLabel(Context c) {
    return ModelOptions.fastLabel(
        fast(c), c.getSharedPreferences("models", 0).getString("fast_served_" + discussion(c), ""));
  }

  public static void recordTier(Context c, String model, String requested, String served) {
    if (!requested.equals("priority")) return;
    if (!(served.equals("priority") || served.equals("fast") || served.equals("default"))) return;
    c.getSharedPreferences("models", 0).edit().putString("fast_served_" + model, served).apply();
  }

  public static String serviceTier(Context c) {
    return fast(c) ? "priority" : "default";
  }

  public static String reasoning(Context c, String kind) {
    String model = kind.equals("summary") ? summary(c) : discussion(c);
    String selected =
        c.getSharedPreferences("models", 0).getString("reasoning_" + kind + "_" + model, "");
    String catalog = c.getSharedPreferences("models", 0).getString("catalog", "{}");
    return ModelOptions.reasoning(catalog, model).contains(selected) ? selected : "";
  }

  public static void reasoning(Context c, String kind, String effort) {
    if (!(kind.equals("discussion") || kind.equals("summary")))
      throw new IllegalArgumentException("kind");
    String model = kind.equals("summary") ? summary(c) : discussion(c);
    String catalog = c.getSharedPreferences("models", 0).getString("catalog", "{}");
    if (!ModelOptions.reasoning(catalog, model).contains(effort))
      throw new IllegalArgumentException("effort");
    c.getSharedPreferences("models", 0)
        .edit()
        .putString("reasoning_" + kind + "_" + model, effort)
        .apply();
  }

  public static void set(Context c, String kind, String model) {
    if (!ModelOptions.valid(model)
        || !(kind.equals("discussion")
            || kind.equals("summary")
            || kind.equals("live")
            || kind.equals("digests"))) throw new IllegalArgumentException("model");
    if (kind.equals("discussion")) digests(c);
    c.getSharedPreferences("models", 0).edit().putString(kind, model).apply();
  }

  private static String read(Context c, String key, String fallback) {
    try {
      String id = c.getSharedPreferences("models", 0).getString(key, fallback);
      return ModelOptions.valid(id) ? id : fallback;
    } catch (ClassCastException e) {
      return fallback;
    }
  }
}
