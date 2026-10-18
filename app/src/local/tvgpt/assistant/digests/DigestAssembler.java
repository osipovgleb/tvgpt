package local.tvgpt.assistant.digests;

import android.content.Context;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import local.tvgpt.assistant.*;
import local.tvgpt.assistant.data.*;
import org.json.*;

/** Text publication precedes optional generation; imagegen can never block the news release. */
public final class DigestAssembler {
  private final Context context;
  private final ImageGenerator generator;

  public DigestAssembler(Context c, ImageGenerator g) {
    context = c.getApplicationContext();
    generator = g;
  }

  public void run(String jobId, AtomicBoolean stopped) {
    DigestRepository repo = new DigestRepository(context);
    DigestImages images = new DigestImages(context);
    try {
      JSONObject snapshot = repo.job(jobId);
      if (snapshot == null || stopped.get()) return;
      if (!DigestResearch.authorized(context)) {
        repo.finish(jobId, "requires-login", "Требуется вход");
        return;
      }
      JSONArray articles = repo.persistedArticles(jobId);
      boolean persisted = articles != null;
      if (!persisted && snapshot.has("cover_article")) {
        repo.finish(jobId, "failed", "Новость больше не доступна");
        return;
      }
      if (!persisted)
        articles = new DigestResearch(context).collect(snapshot, System.currentTimeMillis());
      if (stopped.get()) {
        repo.finish(jobId, "waiting", "Ожидает продолжения");
        return;
      }
      if (articles.length() == 0) {
        repo.finish(jobId, "partial", "Свежие новости не найдены");
        return;
      }
      if (!persisted && !repo.publish(jobId, articles, System.currentTimeMillis())) {
        repo.finish(jobId, "cancelled", "Подборка изменилась");
        return;
      }
      changed();
      boolean partial = false;
      DigestCovers covers = new DigestCovers(repo, images, generator);
      boolean[] covered = new boolean[articles.length()];
      for (int i = 0; i < articles.length() && !stopped.get(); i++) {
        covered[i] = covers.attachSource(jobId, articles.getJSONObject(i), stopped);
        changed();
      }
      int sourceCovers = 0;
      for (boolean ready : covered) if (ready) sourceCovers++;
      android.util.Log.i("TVGPTDigest", "Source covers=" + sourceCovers + "/" + articles.length());
      for (int i = 0; i < articles.length() && !stopped.get(); i++) {
        JSONObject article = articles.getJSONObject(i);
        if (snapshot.has("cover_article")
            && !article.getString("id").equals(snapshot.getString("cover_article"))) continue;
        covered[i] = covers.attachGenerated(jobId, snapshot, article, stopped) || covered[i];
        changed();
        images.trim(200L * 1024 * 1024);
      }
      for (boolean ready : covered) if (!ready) partial = true;
      repo.finish(
          jobId,
          stopped.get()
              ? "waiting"
              : DigestRunPolicy.result(
                  snapshot.has("cover_article"), covers.generationState(), !partial),
          snapshot.has("cover_article") && !covers.generationState().equals("ready")
              ? (covers.generationReason().isEmpty()
                  ? "Новая иллюстрация не получена; прежняя обложка сохранена"
                  : covers.generationReason() + "; прежняя обложка сохранена")
              : partial ? "Выпуск готов; часть новостей без изображения" : "");
    } catch (Exception e) {
      repo.finish(
          jobId,
          stopped.get() ? "waiting" : "failed",
          "Обновление не завершено; предыдущий выпуск сохранён");
      android.util.Log.w("TVGPTDigest", "Update: " + e.getClass().getSimpleName());
    } finally {
      repo.prune(System.currentTimeMillis());
      changed();
      images.trim(200L * 1024 * 1024);
    }
  }

  private void changed() {
    context.sendBroadcast(
        new android.content.Intent("local.tvgpt.assistant.DIGEST_CHANGED")
            .setPackage(context.getPackageName()));
  }
}
