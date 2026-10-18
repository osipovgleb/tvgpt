package local.tvgpt.assistant.digests;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import local.tvgpt.assistant.data.DigestRepository;
import org.json.*;

/** Real SQLite/cover orchestration; only the external image generator is replaced. */
public final class DigestCoverChecks {
  public static void run(Context c, Bundle out) throws Exception {
    DigestRepository repo = new DigestRepository(c);
    JSONObject created =
        repo.mutate(
            UUID.randomUUID().toString(),
            "digest",
            new JSONObject().put("action", "create").put("title", "Fallback").put("zone", "UTC"),
            0);
    String digestId = created.getString("id");
    long now = System.currentTimeMillis();
    JSONObject queued = repo.enqueue(digestId, UUID.randomUUID().toString(), "", 0, now);
    repo.claim(now, now + 10000);
    String jobId = queued.getString("job_id");
    File photo = new File(c.getFilesDir(), "source-photo.png");
    Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
    try (FileOutputStream stream = new FileOutputStream(photo)) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
    }
    bitmap.recycle();
    JSONObject article =
        new JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("title", "Website photo survives")
            .put("url", "https://example.org/fallback")
            .put("image_path", photo.getAbsolutePath())
            .put("image_kind", "source");
    if (!repo.publish(jobId, new JSONArray().put(article), now))
      throw new AssertionError("release not published");
    ImageGenerator unavailable =
        (operation, prompt, destination) ->
            new ImageGenerator.Result("failed", null, "", "Unavailable");
    DigestCovers covers = new DigestCovers(repo, new DigestImages(c), unavailable);
    if (!covers.attachSource(jobId, article, new AtomicBoolean()))
      throw new AssertionError("source photo missing");
    if (covers.attachGenerated(jobId, repo.job(jobId), article, new AtomicBoolean()))
      throw new AssertionError("failed generation accepted");
    JSONObject retained = repo.cards(digestId).getJSONObject(0);
    if (!retained.getString("image_kind").equals("source")
        || !retained.getString("image_path").equals(photo.getAbsolutePath()))
      throw new AssertionError("imagegen blocked fallback");
    repo.finish(jobId, "ready", "");
    repo.mutate(
        UUID.randomUUID().toString(),
        "digest",
        new JSONObject().put("action", "delete").put("id", digestId),
        1);
    photo.delete();
    out.putBoolean("website_photo_survives_imagegen_failure", true);
  }
}
