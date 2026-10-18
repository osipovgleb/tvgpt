package local.tvgpt.assistant;

import java.nio.file.Files;
import org.json.*;

public final class AgentConversationTest {
  public static void main(String[] args) throws Exception {
    Http.reset(0);
    SessionStore store = new SessionStore(Files.createTempDirectory("tvgpt-call-context").toFile());
    Agent first = new Agent(null, store.open(1));
    first.run("Открой YouTube");
    if (!"default".equals(Http.lastTier)) throw new AssertionError("Standard tier not sent");
    ModelSettings.tier = "priority";
    first.run("Найди гонки");
    if (!"priority".equals(Http.lastTier)) throw new AssertionError("Fast tier not sent");
    if (!"model=gpt-6.1-sol;tier=priority".equals(Http.lastRouting))
      throw new AssertionError("Codex Fast routing hint is missing or mismatches payload");
    ModelSettings.tier = "default";
    ModelSettings.effort = "low";
    first.run("Проверь настройки");
    if (!"low".equals(Http.lastEffort))
      throw new AssertionError("Selected reasoning effort not sent");
    ModelSettings.effort = "";
    first.run("Авто");
    if (!Http.lastEffort.isEmpty()) throw new AssertionError("Auto reasoning must omit override");
    String withinCall = Http.lastInput.toString();
    if (!withinCall.contains("Открой YouTube") || !withinCall.contains("Найди гонки"))
      throw new AssertionError("Delegations within a conversation lost their shared context");
    new Agent(null, store.open(2)).run("Поставь на паузу");
    String nextCall = Http.lastInput.toString();
    if (nextCall.contains("YouTube") || nextCall.contains("гонки"))
      throw new AssertionError("Fresh call inherited old agent instructions");
    Http.reset(1);
    Http.toolName = "knowledge_save";
    new Agent(null, store.open(3)).run("Запомни: моё тестовое число37");
    if (Http.requests != 2 || TvTools.actions != 1)
      throw new AssertionError("Knowledge save repeated or confirmation failed");
    JSONArray cited =
        new JSONArray(
            "[{\"content\":[{\"annotations\":[{\"type\":\"url_citation\",\"url\":\"https://example.org/source\"},{\"type\":\"url_citation\",\"url\":\"javascript:bad\"}]}]}]");
    String sources = WebSources.extract(cited);
    if (!sources.contains("https://example.org/source") || sources.contains("javascript:"))
      throw new AssertionError("Web source links missing or unsafe");
    boolean search = false, read = false, web = false, appSkill = false;
    JSONArray tools = Agent.tools();
    for (int i = 0; i < tools.length(); i++) {
      web |=
          tools.getJSONObject(i).optString("type").equals("web_search")
              && tools.getJSONObject(i).optBoolean("external_web_access");
      appSkill |= tools.getJSONObject(i).optString("name").equals("load_app_skill");
      search |= tools.getJSONObject(i).optString("name").equals("conversation_search");
      read |= tools.getJSONObject(i).optString("name").equals("conversation_read");
    }
    if (!appSkill) throw new AssertionError("App skills are not discoverable by the executor");
    Http.reset(0);
    Http.scripted =
        new JSONArray[] {
          new JSONArray()
              .put(
                  new JSONObject()
                      .put("type", "function_call")
                      .put("name", "load_app_skill")
                      .put("call_id", "skill-read")
                      .put("arguments", "{\"name\":\"youtube\"}")),
          new JSONArray()
              .put(
                  new JSONObject()
                      .put("type", "message")
                      .put("phase", "final_answer")
                      .put(
                          "content",
                          new JSONArray()
                              .put(
                                  new JSONObject()
                                      .put("type", "output_text")
                                      .put("text", "Готово"))))
        };
    new Agent(null).run("Прочитай встроенный навык YouTube");
    if (Http.requests != 2 || AssistantSkills.loads != 1 || TvTools.actions != 0)
      throw new AssertionError(
          "Loading an app skill must be read-only and available in tool context");
    if (!web) throw new AssertionError("Live internet search is not registered");
    if (!search || !read)
      throw new AssertionError("Explicit archive recall has no retrieval tools");
    System.out.println(
        "AgentConversationTest: within-call continuity, fresh-call isolation and explicit recall"
            + " tools passed");
  }
}
