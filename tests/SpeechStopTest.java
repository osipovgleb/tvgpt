package local.tvgpt.assistant;

import org.json.JSONObject;

public class SpeechStopTest {
  static JSONObject chunk(String text) throws Exception {
    return new JSONObject()
        .put("type", "input_transcript.added")
        .put("item", new JSONObject().put("text", text));
  }

  public static void main(String[] args) throws Exception {
    SpeechStop stop = new SpeechStop();
    stop.accept(chunk("Заверши "), 0);
    stop.accept(chunk("голосовой чат"), 100);
    if (stop.due(400) || !stop.due(550) || stop.due(600))
      throw new AssertionError("Explicit stable close must fire once before cloud turn.done");
    stop = new SpeechStop();
    stop.accept(chunk("Спасибо"), 0);
    if (stop.due(2000)) throw new AssertionError("Partial thanks is not a close command");
    stop = new SpeechStop();
    stop.accept(chunk("Заверши голосовой чат"), 0);
    stop.accept(chunk(" не сейчас"), 200);
    if (stop.due(1000)) throw new AssertionError("Continuation must cancel pending close");
    stop = new SpeechStop();
    stop.accept(chunk("Заверши "), 0);
    stop.accept(chunk("Заверши "), 10);
    stop.accept(chunk("голосовой чат"), 100);
    if (!stop.due(600)) throw new AssertionError("Duplicate delivery must not corrupt transcript");
    stop = new SpeechStop();
    JSONObject mirrored = chunk("Заверши голосовой чат").put("start_ms", 100).put("end_ms", 900);
    mirrored.getJSONObject("item").put("id", "input-one");
    stop.accept(mirrored, 0);
    stop.accept(mirrored, 350);
    if (!stop.due(500)) throw new AssertionError("Delayed sideband mirror cancelled local closing");
    stop = new SpeechStop();
    stop.accept(chunk("На этом всё, спасибо"), 0);
    if (!stop.due(500))
      throw new AssertionError("Complete natural goodbye waited for a cloud turn");
    stop = new SpeechStop();
    stop.accept(chunk("Ну всё, наверное, спасибо большое"), 0);
    if (!stop.due(500)) throw new AssertionError("Natural closing fillers waited for the agent");
    stop = new SpeechStop();
    stop.accept(
        new JSONObject()
            .put("type", "output_transcript.added")
            .put("item", new JSONObject().put("text", "Заверши голосовой чат")),
        0);
    if (stop.due(1000)) throw new AssertionError("Assistant output cannot end conversation");
    System.out.println(
        "SpeechStopTest: explicit partial close, continuation, duplicates and assistant exclusion"
            + " passed");
  }
}
