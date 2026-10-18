package local.tvgpt.assistant;

import java.io.*;
import java.nio.file.*;
import org.json.*;

public class SessionStoreTest {
  public static void main(String[] args) throws Exception {
    File directory = Files.createTempDirectory("tvgpt-session-").toFile();
    long hour = 3600000L;
    java.util.List<String> archived = new java.util.ArrayList<>();
    SessionStore store = new SessionStore(directory, state -> archived.add(state.id));
    SessionStore.State first = store.open(100 * hour);
    first.history.put(new JSONObject().put("role", "user").put("content", "Открой YouTube"));
    store.save(first);
    first.transcript.put(new JSONObject().put("role", "user").put("text", "Секрет прошлой беседы"));
    store.save(first);
    if (first.history.length() != 1 || first.transcript.length() != 1)
      throw new AssertionError("Current conversation lost its own context");
    SessionStore.State next = store.open(100 * hour + 1);
    if (first.id.equals(next.id) || next.history.length() != 0 || next.transcript.length() != 0)
      throw new AssertionError("New invocation inherited the previous conversation");
    if (!archived.contains(first.id) || first.transcript.length() != 1)
      throw new AssertionError("Starting fresh must archive, not erase the previous conversation");
    SessionStore.State restarted =
        new SessionStore(directory, state -> archived.add(state.id)).open(101 * hour);
    if (restarted.id.equals(next.id) || restarted.history.length() != 0)
      throw new AssertionError("Process restart resurrected the previous context");
    if (!archived.contains(next.id)) throw new AssertionError("Disk conversation was not archived");
    SessionStore rotating = new SessionStore(directory);
    SessionStore.State obsolete = rotating.open(126 * hour);
    SessionStore.State replacement = rotating.open(139 * hour);
    rotating.save(obsolete);
    JSONObject persisted =
        new JSONObject(Files.readString(new File(directory, "session.json").toPath()));
    if (!persisted.getString("id").equals(replacement.id))
      throw new AssertionError("Old session overwrote its replacement");
    JSONArray evidence =
        new JSONArray()
            .put(new JSONObject().put("type", "input_text").put("text", "Observed player title"))
            .put(
                new JSONObject()
                    .put("type", "input_image")
                    .put("image_url", "data:image/jpeg;base64,PRIVATE_IMAGE"))
            .put(new JSONObject().put("type", "input_audio").put("data", "PRIVATE_AUDIO"))
            .put(new JSONObject().put("type", "input_text").put("text", "x".repeat(10000)));
    replacement.history.put(
        new JSONObject()
            .put("type", "function_call_output")
            .put("call_id", "observed-action")
            .put("output", evidence));
    String liveBefore = replacement.history.toString();
    rotating.save(replacement);
    rotating.save(
        replacement); // Turn archival can save while the executor still needs its evidence.
    if (!replacement.history.toString().equals(liveBefore))
      throw new AssertionError("Saving erased live action evidence before the next Agent request");
    String saved = Files.readString(new File(directory, "session.json").toPath());
    JSONObject savedResult = new JSONObject(saved).getJSONArray("history").getJSONObject(0);
    if (!savedResult.optString("call_id").equals("observed-action")
        || !savedResult.getString("output").contains("Observed player title")
        || savedResult.getString("output").length() > 8192
        || saved.contains("PRIVATE_IMAGE")
        || saved.contains("PRIVATE_AUDIO")
        || saved.contains("base64"))
      throw new AssertionError("Disk evidence must retain bounded receipts without private media");
    for (int i = 0; i < 5; i++) {
      replacement.history.put(
          new JSONObject()
              .put("type", "function_call_output")
              .put("call_id", "screen-" + i)
              .put(
                  "output",
                  new JSONArray()
                      .put(
                          new JSONObject()
                              .put("type", "input_text")
                              .put("text", "Verified title " + i))
                      .put(
                          new JSONObject()
                              .put("type", "input_image")
                              .put("image_url", "image-" + i))));
    }
    SessionStore.retainRecentVisuals(replacement.history);
    int retained = 0;
    for (int i = 0; i < replacement.history.length(); i++) {
      JSONObject item = replacement.history.getJSONObject(i);
      if (item.opt("output") instanceof JSONArray) retained++;
      if (i > 0 && !item.opt("output").toString().contains("Verified title " + (i - 1)))
        throw new AssertionError("Visual retention lost textual action evidence");
    }
    if (retained != 2
        || !(replacement.history.getJSONObject(5).opt("output") instanceof JSONArray)
        || !(replacement.history.getJSONObject(4).opt("output") instanceof JSONArray))
      throw new AssertionError("Keep exactly the two freshest visual observations on ARM32");
    if (directory.listFiles().length == 0)
      throw new AssertionError("Nothing persisted in workspace");
    System.out.println(
        "SessionStoreTest: fresh invocations, within-call context, archival and late-write"
            + " isolation passed");
  }
}
