package local.tvgpt.assistant;

import java.util.*;
import org.json.*;

/** Current call only. Snapshots are detached at admission, never loaded from archives. */
final class CallContext {
  private final LinkedHashMap<String, JSONObject> turns = new LinkedHashMap<>();
  private long unidentified;
  private String currentUser = "";
  private long currentUserStart = -1;
  private final Set<String> seenUsers = new LinkedHashSet<>();

  synchronized boolean isCurrent(String inputId) {
    return inputId.isEmpty() || currentUser.isEmpty() || currentUser.equals(inputId);
  }

  synchronized void accept(JSONObject event) throws Exception {
    JSONObject turn = event.optJSONObject("turn");
    String type = event.optString("type");
    if ((!type.equals("turn.done") && !type.equals("turn.created")) || turn == null) return;
    String role = turn.optString("role"), text = turn.optString("transcript");
    String turnId = turn.optString("id");
    if (role.equals("user") && !turnId.isEmpty() && seenUsers.add(turnId)) {
      long start = turn.optLong("start_ms", currentUserStart + 1);
      if (start >= currentUserStart) {
        currentUser = turnId;
        currentUserStart = start;
      }
      while (seenUsers.size() > 128) seenUsers.remove(seenUsers.iterator().next());
    }
    if (!type.equals("turn.done")) return;
    if ((!role.equals("user") && !role.equals("assistant")) || text.isEmpty()) return;
    String id = turn.optString("id", event.optString("event_id"));
    if (id.isEmpty()) id = "unknown-" + (++unidentified);
    turns.put(id, new JSONObject().put("role", role).put("content", clip(text, 2000)));
    while (turns.size() > 32) turns.remove(turns.keySet().iterator().next());
  }

  synchronized JSONArray snapshot(String requestId, String request) throws Exception {
    List<JSONObject> selected = new ArrayList<>();
    int chars = 0;
    List<Map.Entry<String, JSONObject>> entries = new ArrayList<>(turns.entrySet());
    int cutoff = entries.size();
    for (int i = 0; i < entries.size(); i++)
      if (!requestId.isEmpty() && entries.get(i).getKey().equals(requestId)) {
        cutoff = i;
        break;
      }
    for (int i = cutoff - 1; i >= 0 && selected.size() < 16; i--) {
      Map.Entry<String, JSONObject> entry = entries.get(i);
      JSONObject item = entry.getValue();
      if (!requestId.isEmpty() && entry.getKey().equals(requestId)) continue;
      if (cutoff == entries.size()
          && i == entries.size() - 1
          && item.optString("role").equals("user")
          && item.optString("content").equals(request)) continue;
      String text = item.getString("content");
      if (chars + text.length() > 8000) break;
      chars += text.length();
      selected.add(new JSONObject(item.toString()));
    }
    JSONArray result = new JSONArray();
    for (int i = selected.size() - 1; i >= 0; i--) result.put(selected.get(i));
    return result;
  }

  private static String clip(String text, int limit) {
    int end = Math.min(text.length(), limit);
    if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
    return text.substring(0, end);
  }
}
