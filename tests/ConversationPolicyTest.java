package local.tvgpt.assistant;

import org.json.*;

public class ConversationPolicyTest {
  public static void main(String[] args) throws Exception {
    for (String text :
        new String[] {"Спасибо", "Пока", "Goodbye", "Au revoir", "Adiós", "Не ставь на паузу"}) {
      JSONObject event =
          new JSONObject()
              .put("type", "delegation.created")
              .put(
                  "item",
                  new JSONObject()
                      .put("id", "handoff")
                      .put("type", "delegation")
                      .put("target", "client")
                      .put(
                          "content",
                          new JSONArray()
                              .put(new JSONObject().put("type", "input_text").put("text", text))));
      if (!text.equals(LiveProtocol.delegationText(event)))
        throw new AssertionError("Original user request altered");
    }
    System.out.println("ConversationPolicyTest: natural requests go unchanged to model tools");
  }
}
