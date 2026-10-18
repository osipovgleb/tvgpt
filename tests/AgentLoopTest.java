package local.tvgpt.assistant;

import org.json.*;

public class AgentLoopTest {
  public static void main(String[] args) throws Exception {
    Http.reset(0);
    final int[] ended = {0};
    Http.scripted =
        new JSONArray[] {
          new JSONArray()
              .put(
                  new JSONObject()
                      .put("type", "function_call")
                      .put("name", "end_conversation")
                      .put("call_id", "close-1")
                      .put("arguments", "{}"))
        };
    new Agent(null, SessionWorkspace.open(null), () -> ended[0]++).run("Au revoir, à demain");
    if (ended[0] != 1 || Http.requests != 1) throw new AssertionError("Real end tool not invoked");
    Http.reset(8);
    String result = new Agent(null).run("Выполни восемь последовательных действий");
    if (TvTools.actions != 8 || !result.equals("Готово"))
      throw new AssertionError("Request cut off after " + TvTools.actions + " actions: " + result);
    Http.reset(1000);
    Agent ongoing = new Agent(null);
    result = ongoing.run("Длинное поручение");
    if (!Http.finalized || !result.equals("Этап завершён, можно продолжить."))
      throw new AssertionError("Budget exhaustion skipped final explanation");
    int prior = TvTools.actions;
    Http.requestedActions = prior + 2;
    result = ongoing.run("Продолжай");
    if (TvTools.actions != prior + 2 || !result.equals("Готово"))
      throw new AssertionError("Cannot continue after budget pause");
    Http.reset(0);
    Http.scripted =
        new JSONArray[] {
          message("commentary", "Сейчас открою"),
          new JSONArray()
              .put(
                  new JSONObject()
                      .put("type", "function_call")
                      .put("name", "navigate")
                      .put("call_id", "real-action")
                      .put("arguments", "{\"direction\":\"home\"}")),
          message("final_answer", "Открыт главный экран")
        };
    result = new Agent(null).run("Выйди на главный экран");
    if (Http.requests != 3 || TvTools.actions != 1 || !result.equals("Открыт главный экран"))
      throw new AssertionError("Commentary stopped execution before the actual action: " + result);
    if (!Http.lastInput.toString().contains("commentary"))
      throw new AssertionError("Assistant phase lost when replaying context");
    JSONArray action = Http.scripted[1];
    Http.reset(0);
    Http.scripted =
        new JSONArray[] {
          message("commentary", "Проверяю"),
          message("commentary", "Открываю"),
          action,
          message("commentary", "Проверяю результат"),
          message("commentary", "Завершаю проверку"),
          message("final_answer", "Открыт главный экран")
        };
    result = new Agent(null).run("Выйди на главный экран");
    if (Http.requests != 6 || TvTools.actions != 1 || !result.equals("Открыт главный экран"))
      throw new AssertionError("A real tool must reset the commentary-only counter");
    Http.reset(0);
    Http.scripted = new JSONArray[] {message("commentary", "Сейчас сделаю")};
    result = new Agent(null).run("Открой приложение");
    if (Http.requests > 4 || result.contains("Сейчас сделаю"))
      throw new AssertionError("Repeated commentary must stop boundedly without claiming action");
    Http.reset(0);
    Http.scripted =
        new JSONArray[] {
          message("commentary", "Проверяю")
              .put(message("final_answer", "Не нашёл это приложение").getJSONObject(0))
        };
    result = new Agent(null).run("Открой отсутствующее приложение");
    if (!result.equals("Не нашёл это приложение") || Http.requests != 1)
      throw new AssertionError("Commentary leaked into final result or clarification was retried");
    System.out.println("AgentLoopTest: >6 actions, final explanation, and continuation passed");
  }

  private static JSONArray message(String phase, String text) throws Exception {
    return new JSONArray()
        .put(
            new JSONObject()
                .put("type", "message")
                .put("role", "assistant")
                .put("phase", phase)
                .put(
                    "content",
                    new JSONArray()
                        .put(new JSONObject().put("type", "output_text").put("text", text))));
  }
}
