package local.tvgpt.assistant.data;

import org.json.*;

public final class ConversationRepository {
  public static JSONArray records = new JSONArray();
  public static boolean deleted;

  public ConversationRepository(android.content.Context context) {}

  public boolean appendTurn(
      String id, String call, String event, long at, String role, String text) {
    return true;
  }

  public JSONArray search(String q, long since, long until, String current, int limit) {
    return new JSONArray();
  }

  public JSONArray search(String q, long since, long until, String current, int limit, int offset) {
    return new JSONArray();
  }

  public boolean deleteIfCurrent(
      String id, long revision, local.tvgpt.assistant.CancellationToken token) {
    token.check();
    if (deleted || revision != 1) return false;
    deleted = true;
    return true;
  }

  public JSONObject get(String id) throws Exception {
    return deleted ? null : new JSONObject().put("id", id).put("revision", 1);
  }

  public JSONArray turns(String id, int limit, int offset) {
    JSONArray out = new JSONArray();
    for (int i = offset; i < Math.min(records.length(), offset + limit); i++)
      out.put(records.opt(i));
    return out;
  }
}
