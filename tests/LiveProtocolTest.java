package local.tvgpt.assistant;

import org.json.*;

public class LiveProtocolTest {
  public static void main(String[] a) throws Exception {
    JSONObject user = LiveProtocol.userInput("Открой YouTube");
    if (user.has("channel"))
      throw new AssertionError("User command must not be forced into speakable output channel");
    if (!user.getJSONArray("content").getJSONObject(0).getString("text").equals("Открой YouTube"))
      throw new AssertionError("User input changed");
    JSONObject speech = LiveProtocol.speechOutput("Образец голоса");
    if (!speech.optString("channel").equals("speakable")
        || !speech.optString("type").equals("session.context.append"))
      throw new AssertionError("Preview needs explicit speakable output, not inert user context");
    JSONObject resultUpdate =
        LiveProtocol.delegationResult(
            "d1", "{\"status\":\"observed\",\"speech\":\"Открыто\"}", false);
    if (resultUpdate.has("channel") || !resultUpdate.optString("delegation_item_id").equals("d1"))
      throw new AssertionError(
          "Backend facts must not force stale speech over the current user turn");
    if (!LiveProtocol.delegationResult("current", "Готово", true)
        .optString("channel")
        .equals("speakable"))
      throw new AssertionError("Current task result must be heard promptly");
    JSONObject d =
        new JSONObject(
            "{\"type\":\"delegation.created\",\"item\":{\"id\":\"d1\",\"type\":\"delegation\",\"target\":\"client\",\"content\":[{\"type\":\"input_text\",\"text\":\"Открой"
                + " YouTube\"}]}}");
    if (!LiveProtocol.delegationText(d).equals("Открой YouTube"))
      throw new AssertionError("lost delegation");
    d.getJSONObject("item").put("target", "server");
    if (LiveProtocol.delegationText(d) != null) throw new AssertionError("wrong target accepted");
    String text = "🎤Русский текст ".repeat(80);
    StringBuilder result = new StringBuilder();
    for (String part : LiveProtocol.chunks(text)) {
      if (part.getBytes("UTF-8").length > 500) throw new AssertionError("overflow");
      result.append(part);
    }
    if (!result.toString().equals(text)) throw new AssertionError("unicode corrupted");
    if (LiveProtocol.completedResponse("data: {\"type\":\"response.failed\"}\n\n") != null)
      throw new AssertionError("failure is not completion");
    String streamed =
        "data:"
            + " {\"type\":\"response.output_item.done\",\"output_index\":1,\"item\":{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Найдено\",\"annotations\":[{\"type\":\"url_citation\",\"url\":\"https://example.org\"}]}]}}\n\n"
            + "data:"
            + " {\"type\":\"response.output_item.done\",\"output_index\":0,\"item\":{\"type\":\"web_search_call\",\"status\":\"completed\"}}\n\n"
            + "data:"
            + " {\"type\":\"response.completed\",\"response\":{\"id\":\"response-test\",\"output\":[]}}\n\n";
    JSONArray assembled = LiveProtocol.completedResponse(streamed).getJSONArray("output");
    if (assembled.length() != 2
        || !assembled.getJSONObject(0).optString("type").equals("web_search_call")
        || !assembled.getJSONObject(1).getJSONArray("content").getJSONObject(0).has("annotations"))
      throw new AssertionError("Streamed search, answer or citations lost at empty completion");
    if (LiveProtocol.completedResponse(
            streamed.substring(0, streamed.indexOf("data: {\"type\":\"response.completed")))
        != null) throw new AssertionError("Partial stream accepted without completion");
    System.out.println(
        "LiveProtocolTest: delegation, routing, Unicode and response completion passed");
  }
}
