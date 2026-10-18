package local.tvgpt.assistant;

import org.json.*;

public class ConversationPolicyTest {
  public static void main(String[] args) throws Exception {
    for (String s :
        new String[] {
          "Заверши голосовой чат.",
          "На этом всё, спасибо!",
          "Все, спасибо",
          "Закончи разговор",
          "Отключись",
          "Спасибо, до свидания",
          "Заверши сессию",
          "Заверши, пожалуйста, голосовой чат",
          "Давай на этом закончим",
          "Всё, пока",
          "Хватит",
          "Выключи помощника",
          "Можешь завершить разговор",
          "Останови голосовой режим",
          "Спасибо, всё",
          "Всё, на этом всё, спасибо",
          "Спасибо",
          "Ну всё, наверное, спасибо большое",
          "Ладно, на этом всё, спасибо большое"
        }) if (!EndPhrase.matches(s)) throw new AssertionError("Missed ending: " + s);
    for (String s :
        new String[] {
          "Спасибо, открой YouTube",
          "Не завершай голосовой чат",
          "Скажи: заверши голосовой чат",
          "На этом всё не заканчивается",
          "Как завершить голосовой чат?",
          "Ну всё, наверное, спасибо большое, открой YouTube",
          "Ну всё наверное не заканчивается"
        }) if (EndPhrase.matches(s)) throw new AssertionError("Closed on ordinary speech: " + s);
    for (String s :
        new String[] {
          "TVGPT_END_CONVERSATION", "Завершить текущий голосовой разговор.", "Закрой сессию Live"
        })
      if (!LiveProtocol.localEndDelegation(s))
        throw new AssertionError("Closing delegation must bypass agent: " + s);
    for (String s :
        new String[] {
          "Открой YouTube", "Объясни как завершить разговор", "Не завершай голосовой чат"
        })
      if (LiveProtocol.localEndDelegation(s))
        throw new AssertionError("Ordinary delegation must not close: " + s);
    if (!"pause".equals(LiveProtocol.localMediaAction("Поставь на паузу"))
        || !"pause".equals(LiveProtocol.localMediaAction("TVGPT_MEDIA_PAUSE")))
      throw new AssertionError("Pause must use direct media command");
    if (LiveProtocol.localMediaAction("Не ставь на паузу") != null)
      throw new AssertionError("Negation must not pause");
    JSONObject user =
        new JSONObject(
            "{\"type\":\"turn.done\",\"turn\":{\"role\":\"user\",\"transcript\":\"На этом всё,"
                + " спасибо\"}}");
    if (!LiveProtocol.endsConversation(user))
      throw new AssertionError("Final user turn must close");
    user.getJSONObject("turn").put("role", "assistant");
    if (LiveProtocol.endsConversation(user))
      throw new AssertionError("Assistant cannot close by quoting user");
    if (LiveProtocol.endsConversation(
        new JSONObject("{\"type\":\"session.input_transcript.delta\",\"delta\":\"На этом всё\"}")))
      throw new AssertionError("Partial speech must not close");
    System.out.println("ConversationPolicyTest passed");
  }
}
