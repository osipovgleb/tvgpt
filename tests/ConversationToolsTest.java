package local.tvgpt.assistant;

import local.tvgpt.assistant.data.ConversationRepository;
import org.json.*;

public final class ConversationToolsTest {
  public static void main(String[] args) throws Exception {
    for (int i = 0; i < 30; i++)
      ConversationRepository.records.put(
          new JSONObject()
              .put("role", i % 2 == 0 ? "user" : "assistant")
              .put("text", "a".repeat(1999) + "🎤" + "z".repeat(2000)));
    JSONObject request = new JSONObject().put("id", "past").put("limit", 100000).put("offset", 0);
    JSONObject page =
        (JSONObject) ConversationTools.execute(null, "conversation_read", request, "current");
    JSONArray messages = page.getJSONArray("messages");
    int size = 0;
    for (int i = 0; i < messages.length(); i++) {
      String text = messages.getJSONObject(i).getString("text");
      size += text.length();
      if (Character.isHighSurrogate(text.charAt(text.length() - 1)))
        throw new AssertionError("Recall split a Unicode character");
    }
    if (size > 12000
        || messages.length() > 20
        || !page.getBoolean("has_more")
        || page.getInt("next_offset") != messages.length())
      throw new AssertionError(
          "Archive recall must be bounded and paginated without losing messages");
    boolean rejected = false;
    try {
      ConversationTools.execute(null, "conversation_read", request.put("id", "current"), "current");
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    if (!rejected)
      throw new AssertionError("Current context must not be retrieved as an old conversation");
    rejected = false;
    ConversationRepository.deleted = true;
    try {
      ConversationTools.execute(null, "conversation_read", request.put("id", "past"), "current");
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    if (!rejected) throw new AssertionError("Deleted archive was read");
    System.out.println(
        "ConversationToolsTest: bounded recall, Unicode, pagination and deleted/current exclusion"
            + " passed");
  }
}
