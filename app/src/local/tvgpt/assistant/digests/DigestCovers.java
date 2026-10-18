package local.tvgpt.assistant.digests;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import local.tvgpt.assistant.data.DigestRepository;
import org.json.*;

/** Optional covers cannot turn a completed text release into a failed digest. */
final class DigestCovers {
  private final DigestRepository repo;
  private final DigestImages images;
  private final ImageGenerator generator;
  private boolean generate = true;
  private String generationState = "unconfirmed";
  private String generationReason = "";

  String generationReason() {
    return generationReason;
  }

  String generationState() {
    return generationState;
  }

  DigestCovers(DigestRepository r, DigestImages i, ImageGenerator g) {
    repo = r;
    images = i;
    generator = g;
  }

  boolean attachGenerated(
      String jobId, JSONObject snapshot, JSONObject article, AtomicBoolean stopped)
      throws Exception {
    String key =
        DigestRunPolicy.generationKey(
            article, "editorial-v1" + (snapshot.has("cover_article") ? jobId : ""));
    File cover = null;
    try {
      cover = generated(jobId, key, snapshot, article, stopped);
    } catch (Exception ignored) {
      generate = false;
    }
    return cover != null
        && !stopped.get()
        && repo.attachCoverKind(
            jobId, article.getString("id"), cover.getAbsolutePath(), key, "generated", "");
  }

  boolean attachSource(String jobId, JSONObject article, AtomicBoolean stopped) throws Exception {
    if (stopped.get()) return false;
    String existing = article.optString("image_path");
    if (!existing.isEmpty() && new File(existing).isFile())
      return repo.attachCoverKind(
          jobId,
          article.getString("id"),
          existing,
          article.optString("image_key"),
          article.optString("image_kind", "source"),
          article.optString("image_origin"));
    String url = article.optString("image_url");
    if (url.isEmpty())
      try {
        JSONArray source = new NewsSourceClient().article(article.getString("url"));
        if (source.length() > 0) url = source.getJSONObject(0).optString("image_url");
      } catch (Exception ignored) {
      }
    if (url.isEmpty()) return false;
    try {
      String key = DigestRunPolicy.coverKey(url, "source-v1");
      File cover = images.siteImage(key, url);
      return !stopped.get()
          && repo.attachCoverKind(
              jobId, article.getString("id"), cover.getAbsolutePath(), key, "source", url);
    } catch (Exception ignored) {
      return false;
    }
  }

  private File generated(
      String jobId, String key, JSONObject snapshot, JSONObject article, AtomicBoolean stopped)
      throws Exception {
    if (!generate) return null;
    JSONObject reservation = repo.reserveGeneration(jobId, key, System.currentTimeMillis());
    String state = reservation.optString("state");
    generationState =
        state.equals("ready") ? "ready" : state.equals("unconfirmed") ? "unconfirmed" : "failed";
    if (state.equals("ready")) {
      File cached = new File(reservation.optString("path"));
      return cached.isFile() ? cached : null;
    }
    if (!state.equals("reserved")) return null;
    File tmp = images.temporary(key);
    File cover = null;
    try {
      ImageGenerator.Result result =
          generator.generate(
              reservation.getString("operation_id"),
              "Горизонтальная редакционная иллюстрация. Кинематографичный свет, сдержанные цвета."
                  + " Не выдавай вымышленную сцену за документальный снимок. Без текста. Новость: "
                  + article.optString("title")
                  + ". "
                  + article.optString("summary")
                  + ". "
                  + snapshot.optString("cover_prompt"),
              tmp);
      generationReason = result.reason;
      if (result.state.equals("ready") && !stopped.get() && repo.canPublish(jobId)) {
        File target = images.file(key);
        if (images.store(tmp, key, 200L * 1024 * 1024)) cover = target;
      }
      repo.generationResult(
          reservation.getString("operation_id"),
          cover == null ? (result.state.equals("ready") ? "unconfirmed" : result.state) : "ready",
          cover == null ? "" : cover.getAbsolutePath(),
          result.reportedModel);
      generationState =
          cover != null ? "ready" : result.state.equals("unconfirmed") ? "unconfirmed" : "failed";
      if (cover == null) generate = false;
      return cover;
    } finally {
      tmp.delete();
    }
  }
}
