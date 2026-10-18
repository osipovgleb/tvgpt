package local.tvgpt.assistant.data;

import org.json.*;

public class ConversationRepository {
  public static long revision = 1;
  public static boolean deleted;
  public static boolean empty;
  public static int saves;
  public static long captured = -1;

  public ConversationRepository(android.content.Context c) {}

  public JSONObject get(String id) throws Exception {
    return deleted ? null : new JSONObject().put("revision", revision).put("summary_revision", -1);
  }

  public JSONArray tail(String id, int n) throws Exception {
    if (empty) return new JSONArray();
    return new JSONArray().put(new JSONObject().put("role", "user").put("text", "topic"));
  }

  public boolean saveSummaryIfCurrent(String id, long r, String title, String text) {
    captured = r;
    if (deleted || r != revision) return false;
    saves++;
    return true;
  }

  public void summaryFailed(String id, long r) {}
}
