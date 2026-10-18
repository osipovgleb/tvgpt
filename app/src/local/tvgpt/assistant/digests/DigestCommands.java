package local.tvgpt.assistant.digests;

import android.content.*;
import java.util.*;
import local.tvgpt.assistant.*;
import local.tvgpt.assistant.data.*;
import org.json.*;

public final class DigestCommands {
  private final Context context;

  public DigestCommands(Context c) {
    context = c.getApplicationContext();
  }

  public JSONObject execute(String tool, JSONObject args) throws Exception {
    return execute(tool, args, CancellationToken.NONE);
  }

  public JSONObject execute(String tool, JSONObject args, CancellationToken token)
      throws Exception {
    token.check();
    DigestToolContract.validate(tool, args);
    DigestRepository repo = new DigestRepository(context, token);
    token.check();
    switch (tool) {
      case "digest_list":
        return new JSONObject().put("items", repo.list("digest", ""));
      case "digest_read":
        return new JSONObject()
            .put("digest", repo.read("digest", args.getString("digest_id")))
            .put("articles", repo.cards(args.getString("digest_id")))
            .put("job", repo.latestJob(args.getString("digest_id")));
      case "digest_source_list":
        return new JSONObject().put("items", repo.list("source", args.getString("digest_id")));
      case "digest_schedule_list":
        return new JSONObject().put("items", repo.list("schedule", args.getString("digest_id")));
      case "digest_source_search":
        return new JSONObject()
            .put("items", new DigestResearch(context, token).discover(args.getString("query")));
      case "digest_job_read":
        return new JSONObject().put("job", repo.job(args.getString("job_id")));
      case "digest_mutate":
      case "digest_source_mutate":
      case "digest_schedule_mutate":
        return mutate(repo, tool, args);
      case "digest_refresh":
        {
          JSONObject result =
              repo.enqueue(
                  args.getString("digest_id"),
                  args.getString("operation_id"),
                  "",
                  0,
                  System.currentTimeMillis());
          DigestScheduler.reconcile(context);
          AppDataEvents.changed(context, AppDataEvents.DIGESTS);
          return status(result);
        }
      case "digest_cover_generate":
        {
          JSONObject result =
              repo.enqueueCover(
                  args.getString("digest_id"),
                  args.getString("operation_id"),
                  args.getString("article_id"),
                  args.getString("prompt"));
          DigestScheduler.reconcile(context);
          AppDataEvents.changed(context, AppDataEvents.DIGESTS);
          return status(result);
        }
      case "digest_open":
        {
          String mode = args.getString("mode");
          if (!mode.equals("detail") && !mode.equals("preview"))
            throw new IllegalArgumentException("Неизвестный режим просмотра");
          Intent intent =
              mode.equals("preview")
                  ? new Intent(context, DigestPreviewActivity.class)
                  : new Intent(context, MainActivity.class).putExtra("section", "DIGESTS");
          intent
              .putExtra("digest_id", args.getString("digest_id"))
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          token.check();
          context.startActivity(intent);
          return new JSONObject().put("status", "requested");
        }
      default:
        throw new IllegalArgumentException("Неизвестный инструмент");
    }
  }

  private JSONObject mutate(DigestRepository repo, String tool, JSONObject args) throws Exception {
    String kind =
        tool.equals("digest_mutate")
            ? "digest"
            : tool.equals("digest_source_mutate") ? "source" : "schedule";
    JSONObject data = new JSONObject(args.toString());
    data.remove("operation_id");
    data.remove("expected_revision");
    if (args.getString("action").equals("create"))
      data.put(
          "zone", new local.tvgpt.assistant.settings.GeneralPreferences(context).zone().getId());
    JSONObject result =
        repo.mutate(args.getString("operation_id"), kind, data, args.getLong("expected_revision"));
    DigestScheduler.reconcile(context);
    if (result.optString("status").equals("saved") || result.optString("status").equals("deleted"))
      AppDataEvents.changed(context, AppDataEvents.DIGESTS);
    return result;
  }

  private JSONObject status(JSONObject job) throws Exception {
    return new JSONObject()
        .put("job_id", job.getString("job_id"))
        .put("state", job.getString("state"))
        .put("status", job.getString("state").equals("queued") ? "queued" : "unconfirmed");
  }
}
