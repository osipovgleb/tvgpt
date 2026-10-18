package local.tvgpt.assistant.settings;

import java.util.*;
import org.json.*;

/** Only catalogued model IDs are offered, with the configured working model as fallback. */
public final class ModelOptions {
  private ModelOptions() {}

  public static List<String> parse(String raw, boolean live, String current) {
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    try {
      JSONArray models = new JSONObject(raw).optJSONArray("models");
      for (int i = 0; models != null && i < models.length(); i++) {
        JSONObject model = models.optJSONObject(i);
        if (model == null || "hide".equals(model.optString("visibility"))) continue;
        String id = model.optString("slug", model.optString("id"));
        if (!valid(id)) continue;
        boolean audio = id.contains("live") || id.contains("realtime");
        if (audio == live) ids.add(id);
      }
    } catch (JSONException ignored) {
    }
    List<String> result = new ArrayList<>(ids);
    if (!ids.contains(current)) result.add(0, current);
    return result;
  }

  public static List<String> reasoning(String raw, String id) {
    LinkedHashSet<String> values = new LinkedHashSet<>();
    values.add(""); // Let the server use the model default unless explicitly selected.
    try {
      JSONArray models = new JSONObject(raw).optJSONArray("models");
      for (int i = 0; models != null && i < models.length(); i++) {
        JSONObject model = models.optJSONObject(i);
        if (model == null || !id.equals(model.optString("slug", model.optString("id")))) continue;
        JSONArray levels = model.optJSONArray("supported_reasoning_levels");
        for (int n = 0; levels != null && n < levels.length(); n++) {
          JSONObject level = levels.optJSONObject(n);
          String effort = level == null ? "" : level.optString("effort");
          if (effort.matches("none|minimal|low|medium|high|xhigh|max|ultra")) values.add(effort);
        }
      }
    } catch (JSONException ignored) {
    }
    return new ArrayList<>(values);
  }

  public static String fastLabel(boolean enabled, String served) {
    if (!enabled) return "Обычный";
    // The subscription backend can echo default even when priority changes generation speed.
    // Keep the raw tier in diagnostics; do not infer a downgrade from that echo.
    return served.equals("priority") || served.equals("fast") ? "Fast" : "Fast запрошен";
  }

  public static String reasoningLabel(String effort) {
    switch (effort) {
      case "none":
        return "Без рассуждений";
      case "minimal":
        return "Минимальный";
      case "low":
        return "Низкий";
      case "medium":
        return "Средний";
      case "high":
        return "Высокий";
      case "xhigh":
        return "Очень высокий";
      case "max":
        return "Максимальный";
      case "ultra":
        return "Ультра";
      default:
        return "Авто";
    }
  }

  public static boolean valid(String id) {
    return id != null && id.matches("[a-zA-Z0-9][a-zA-Z0-9_.:-]{1,95}");
  }
}
