package local.tvgpt.assistant;

import org.json.*;

public class TaskEvidenceTest {
  public static void main(String[] a) throws Exception {
    TaskEvidence e = new TaskEvidence();
    e.record("open", "open_app", "{\"status\":\"confirmed\",\"confirmed_scope\":\"app_visible\"}");
    JSONObject claim =
        new JSONObject()
            .put("status", "observed")
            .put("speech", "видео включено")
            .put("evidence_ids", new JSONArray().put("open"));
    if (e.finish(claim).getBoolean("accepted"))
      throw new AssertionError("app visible proved content");
    e.record(
        "screen",
        "screenshot",
        new JSONArray()
            .put(
                new JSONObject()
                    .put("type", "input_image")
                    .put("image_url", "data:image/png;base64,test")));
    claim.put("evidence_ids", new JSONArray().put("screen"));
    if (!e.finish(claim).getBoolean("accepted")) throw new AssertionError("fresh visual rejected");
    e.record("queue", "digest_create", "{\"status\":\"queued\"}");
    claim.put("evidence_ids", new JSONArray().put("queue"));
    if (e.finish(claim).getBoolean("accepted")) throw new AssertionError("queued promoted");

    TaskEvidence stale = new TaskEvidence();
    stale.record(
        "before", "screenshot", new JSONArray().put(new JSONObject().put("type", "input_image")));
    stale.record("change", "navigate", "{\"status\":\"unconfirmed\"}");
    if (stale
        .finish(
            new JSONObject()
                .put("status", "observed")
                .put("speech", "done")
                .put("evidence_ids", new JSONArray().put("before")))
        .getBoolean("accepted")) throw new AssertionError("stale observation proved new action");
    if (stale
        .finish(
            new JSONObject()
                .put("status", "failed")
                .put("speech", "failed")
                .put("evidence_ids", new JSONArray()))
        .getBoolean("accepted")) throw new AssertionError("uncertainty became failure");
    TaskEvidence requested = new TaskEvidence();
    requested.record("sent", "digest_open", "{\"status\":\"requested\"}");
    JSONObject sent =
        new JSONObject()
            .put("status", "requested")
            .put("speech", "Отправлено")
            .put("evidence_ids", new JSONArray().put("sent"));
    if (!requested.finish(sent).getBoolean("accepted"))
      throw new AssertionError("requested rejected");
    sent.put("status", "observed");
    if (requested.finish(sent).getBoolean("accepted"))
      throw new AssertionError("sent promoted to observed");
    System.out.println(
        "TaskEvidenceTest: sent/open/queued cannot prove content; observation accepted");
  }
}
