package local.tvgpt.assistant;

import java.util.*;
import org.json.*;

/** Receipts qualify a claim; a sent command is not a verified goal. */
final class TaskEvidence {
  private final Map<String, String> scopes = new LinkedHashMap<>();
  private boolean mutation;
  private int sequence, lastMutation;
  private final Map<String, Integer> positions = new HashMap<>();

  static boolean readOnly(String name) {
    return name.equals("read_screen")
        || name.equals("screenshot")
        || name.equals("wait_for_screen")
        || name.equals("list_apps")
        || name.equals("load_app_skill")
        || name.equals("task_status")
        || name.endsWith("_list")
        || name.endsWith("_read")
        || name.endsWith("_search");
  }

  void record(String id, String name, Object result) {
    sequence++;
    if (!readOnly(name) && !name.equals("task_result")) {
      mutation = true;
      lastMutation = sequence;
    }
    String scope = "unconfirmed";
    if (result instanceof JSONArray) {
      JSONArray content = (JSONArray) result;
      for (int i = 0; i < content.length(); i++) {
        JSONObject part = content.optJSONObject(i);
        if (part != null && part.optString("type").equals("input_image"))
          scope = "visual_observation";
      }
    } else {
      try {
        JSONObject data = new JSONObject(String.valueOf(result));
        String status = data.optString("status", data.optString("outcome"));
        String confirmed = data.optString("confirmed_scope");
        if (status.equals("queued")) scope = "queued";
        else if (status.equals("requested")) scope = "requested";
        else if (status.equals("cancelled")) scope = "cancelled";
        else if (status.equals("failed") || status.equals("error") || status.equals("rejected"))
          scope = "failed";
        else if (status.equals("unconfirmed")
            || data.optString("goal_status").equals("unconfirmed")) scope = "unconfirmed";
        else if (!confirmed.isEmpty()) scope = confirmed;
        else if (status.equals("saved")
            || status.equals("created")
            || status.equals("updated")
            || status.equals("deleted")
            || status.equals("cancelled")) scope = "recorded";
        else if (data.has("saved_id") || data.optBoolean("deleted")) scope = "recorded";
        else if ((name.equals("reminder_create") || name.equals("timer_create")) && data.has("id"))
          scope = "queued";
        else if ((name.equals("read_screen") || name.equals("wait_for_screen"))
            && !data.optString("text").trim().isEmpty()) scope = "observation";
      } catch (Exception ignored) {
        // An unstructured error or acknowledgement is not observation evidence.
      }
    }
    scopes.put(id, scope);
    positions.put(id, sequence);
  }

  Object annotate(String id, Object result) throws Exception {
    JSONObject receipt =
        new JSONObject().put("receipt_id", id).put("evidence_scope", scopes.get(id));
    if (result instanceof JSONArray) {
      JSONArray content =
          new JSONArray()
              .put(new JSONObject().put("type", "input_text").put("text", receipt.toString()));
      JSONArray original = (JSONArray) result;
      for (int i = 0; i < original.length(); i++) content.put(original.get(i));
      return content;
    }
    Object data = result;
    if (result instanceof String) {
      String text = (String) result;
      try {
        if (text.startsWith("{")) data = new JSONObject(text);
        else if (text.startsWith("[")) data = new JSONArray(text);
      } catch (Exception ignored) {
      }
    }
    return receipt.put("result", data).toString();
  }

  boolean mutated() {
    return mutation;
  }

  JSONObject finish(JSONObject args) throws Exception {
    String status = args.optString("status"), speech = args.optString("speech");
    JSONArray ids = args.optJSONArray("evidence_ids");
    boolean supported = !mutation && status.equals("answer");
    if (status.equals("unconfirmed")) supported = true;
    if (ids != null)
      for (int i = 0; i < ids.length(); i++) {
        String scope = scopes.get(ids.optString(i));
        if (scope != null && positions.get(ids.optString(i)) < lastMutation) continue;
        if (scope != null && scope.equals("failed") && status.equals("failed")) supported = true;
        if (scope != null && scope.equals("cancelled") && status.equals("cancelled"))
          supported = true;
        if (scope == null || scope.equals("unconfirmed") || scope.equals("failed")) continue;
        if (positions.get(ids.optString(i)) < lastMutation) continue;
        if (status.equals("queued") && scope.equals("queued")) supported = true;
        if (status.equals("observed")
            && !scope.equals("queued")
            && !scope.equals("requested")
            && !scope.equals("cancelled")
            && (!scope.equals("app_visible") || args.optString("goal_scope").equals("app"))
            && !scope.equals("screen_changed")) supported = true;
        if (status.equals("requested")) supported = true;
      }
    if (!supported)
      return new JSONObject()
          .put("accepted", false)
          .put("status", "unconfirmed")
          .put(
              "instruction",
              "Нужны свежие подтверждения цели. Команда отправлена, приложение открыто или экран"
                  + " изменился — ещё не подтверждение контента. Проверь либо заверши с"
                  + " unconfirmed. Пользователю не объясняй внутренний протокол подтверждений,"
                  + " сообщи только понятный результат или препятствие.");
    return new JSONObject()
        .put("accepted", true)
        .put("status", status)
        .put("speech", speech)
        .put("evidence_ids", ids == null ? new JSONArray() : ids);
  }
}
