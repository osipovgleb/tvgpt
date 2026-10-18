package local.tvgpt.assistant.data;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.*;
import java.util.*;
import local.tvgpt.assistant.CancellationToken;
import local.tvgpt.assistant.digests.*;
import org.json.*;

/** Shared storage for voice, UI and background work. All mutations are transactional. */
public final class DigestRepository {
  private static boolean recoveredProcess;
  private final SQLiteDatabase db;
  private final CancellationToken token;

  public DigestRepository(Context c) {
    this(c, CancellationToken.NONE);
  }

  public DigestRepository(Context c, CancellationToken token) {
    this.token = token;
    token.check();
    db = AppDatabase.get(c).getWritableDatabase();
    token.check();
    synchronized (DigestRepository.class) {
      if (!recoveredProcess) {
        token.check();
        db.execSQL("UPDATE digest_jobs SET state='waiting',lease=0 WHERE state='running'");
        recoveredProcess = true;
      }
    }
  }

  private JSONObject one(String sql, String... args) throws Exception {
    try (Cursor c = db.rawQuery(sql, args)) {
      return c.moveToFirst() ? new JSONObject(c.getString(0)) : null;
    }
  }

  private long number(String sql, String... args) {
    try (Cursor c = db.rawQuery(sql, args)) {
      return c.moveToFirst() ? c.getLong(0) : 0;
    }
  }

  private long epoch() {
    return number("SELECT value FROM digest_meta WHERE id='epoch'");
  }

  public JSONArray list(String kind, String parent) throws Exception {
    return new DigestConfiguration(db).list(kind, parent);
  }

  public JSONObject read(String kind, String id) throws Exception {
    return new DigestConfiguration(db).read(kind, id);
  }

  public JSONObject mutate(String operationId, String kind, JSONObject values, long expected)
      throws Exception {
    return new DigestConfiguration(db, token).mutate(operationId, kind, values, expected);
  }

  public JSONObject enqueue(
      String digestId, String operationId, String scheduleId, long slot, long now)
      throws Exception {
    DigestPolicy.id(digestId);
    token.check();
    db.beginTransaction();
    try {
      token.check();
      JSONObject existing = one("SELECT snapshot FROM digest_jobs WHERE operation=?", operationId);
      if (existing != null) {
        db.setTransactionSuccessful();
        return job(existing.getString("job_id"));
      }
      JSONObject digest = read("digest", digestId);
      if (digest == null || !digest.optBoolean("enabled", true))
        throw new IllegalArgumentException("Подборка недоступна");
      try (Cursor c =
          db.rawQuery(
              "SELECT id FROM digest_jobs WHERE digest=? AND state IN"
                  + " ('queued','running','waiting') LIMIT 1",
              new String[] {digestId})) {
        if (c.moveToFirst()) {
          JSONObject active = job(c.getString(0));
          db.setTransactionSuccessful();
          return active;
        }
      }
      String id = UUID.randomUUID().toString();
      JSONObject snapshot =
          new JSONObject()
              .put("job_id", id)
              .put("digest", digest)
              .put("sources", list("source", digestId))
              .put("schedules", list("schedule", digestId))
              .put("schedule_id", scheduleId)
              .put("slot", slot);
      long currentEpoch = epoch();
      token.check();
      db.execSQL(
          "INSERT INTO digest_jobs(id,digest,operation,snapshot,state,created,epoch) VALUES"
              + " (?,?,?,?,?,?,?)",
          new Object[] {
            id, digestId, operationId, snapshot.toString(), "queued", now, currentEpoch
          });
      token.check();
      db.setTransactionSuccessful();
      return job(id);
    } finally {
      db.endTransaction();
    }
  }

  public JSONObject job(String id) throws Exception {
    DigestPolicy.id(id);
    try (Cursor c =
        db.rawQuery(
            "SELECT snapshot,state,reason,created FROM digest_jobs WHERE id=?",
            new String[] {id})) {
      if (!c.moveToFirst()) return null;
      return new JSONObject(c.getString(0))
          .put("state", c.getString(1))
          .put("reason", c.getString(2))
          .put("created", c.getLong(3));
    }
  }

  public JSONObject latestJob(String digestId) throws Exception {
    try (Cursor c =
        db.rawQuery(
            "SELECT id FROM digest_jobs WHERE digest=? ORDER BY created DESC LIMIT 1",
            new String[] {digestId})) {
      return c.moveToFirst() ? job(c.getString(0)) : null;
    }
  }

  public JSONObject enqueueCover(
      String digestId, String operationId, String articleId, String prompt) throws Exception {
    token.check();
    JSONArray cards = cards(digestId);
    boolean exists = false;
    for (int i = 0; i < cards.length(); i++)
      if (cards.getJSONObject(i).getString("id").equals(articleId)) exists = true;
    if (!exists) throw new IllegalArgumentException("Новость не найдена");
    token.check();
    db.beginTransaction();
    try {
      token.check();
      JSONObject pending = enqueue(digestId, operationId, "", 0, System.currentTimeMillis());
      if (!operationId.equals(operation(pending.getString("job_id"))))
        throw new IllegalArgumentException("Сначала дождитесь текущего обновления");
      if (pending.getString("state").equals("queued")) {
        String releaseId = "";
        try (Cursor rows =
            db.rawQuery(
                "SELECT id,articles FROM digest_releases WHERE digest=? ORDER BY created DESC",
                new String[] {digestId})) {
          while (rows.moveToNext() && releaseId.isEmpty()) {
            JSONArray saved = new JSONArray(rows.getString(1));
            for (int i = 0; i < saved.length(); i++)
              if (saved.getJSONObject(i).getString("id").equals(articleId)) {
                releaseId = rows.getString(0);
                break;
              }
          }
        }
        if (releaseId.isEmpty()) throw new IllegalArgumentException("Новость больше не доступна");
        pending
            .put("cover_article", articleId)
            .put("cover_prompt", prompt)
            .put("cover_release", releaseId);
        token.check();
        db.execSQL(
            "UPDATE digest_jobs SET snapshot=? WHERE id=?",
            new Object[] {pending.toString(), pending.getString("job_id")});
      }
      token.check();
      db.setTransactionSuccessful();
      return pending;
    } finally {
      db.endTransaction();
    }
  }

  private String operation(String id) {
    try (Cursor c =
        db.rawQuery("SELECT operation FROM digest_jobs WHERE id=?", new String[] {id})) {
      return c.moveToFirst() ? c.getString(0) : "";
    }
  }

  public JSONObject claim(long now, long leaseUntil) throws Exception {
    db.beginTransaction();
    try {
      String id = null;
      try (Cursor c =
          db.rawQuery(
              "SELECT id FROM digest_jobs WHERE state IN ('queued','waiting') ORDER BY created"
                  + " LIMIT 1",
              null)) {
        if (c.moveToFirst()) id = c.getString(0);
      }
      if (id == null) return null;
      db.execSQL(
          "UPDATE digest_jobs SET state='running',lease=? WHERE id=?",
          new Object[] {leaseUntil, id});
      db.setTransactionSuccessful();
      return job(id);
    } finally {
      db.endTransaction();
    }
  }

  public void finish(String id, String state, String reason) {
    db.execSQL(
        "UPDATE digest_jobs SET state=?,reason=?,lease=0 WHERE id=? AND state!='cancelled'",
        new Object[] {state, reason, id});
  }

  private boolean current(String id) throws Exception {
    JSONObject job = job(id);
    if (job == null || !job.optString("state").equals("running")) return false;
    if (number("SELECT epoch FROM digest_jobs WHERE id=?", id) != epoch()) return false;
    JSONObject digest = job.getJSONObject("digest"), live = read("digest", digest.getString("id"));
    if (live == null
        || !live.optBoolean("enabled", true)
        || live.optLong("revision") != digest.getLong("revision")) return false;
    return job.getJSONArray("sources")
            .toString()
            .equals(list("source", digest.getString("id")).toString())
        && job.getJSONArray("schedules")
            .toString()
            .equals(list("schedule", digest.getString("id")).toString());
  }

  public boolean canPublish(String id) throws Exception {
    return current(id);
  }

  public boolean publish(String id, JSONArray articles, long now) throws Exception {
    db.beginTransaction();
    try {
      if (!current(id) || articles.length() == 0 || job(id).has("cover_article")) return false;
      String digest = job(id).getJSONObject("digest").getString("id");
      JSONArray bounded = DigestPolicy.articles(articles, now);
      for (int i = 0; i < bounded.length(); i++) {
        JSONObject article = bounded.getJSONObject(i);
        String url = article.optString("url");
        db.execSQL(
            "INSERT OR IGNORE INTO digest_seen(url,discovered) VALUES (?,?)",
            new Object[] {url, now});
        article.put("discovered_at", number("SELECT discovered FROM digest_seen WHERE url=?", url));
      }
      db.execSQL(
          "INSERT OR REPLACE INTO digest_releases(id,digest,created,articles) VALUES (?,?,?,?)",
          new Object[] {id, digest, now, bounded.toString()});
      db.execSQL(
          "DELETE FROM digest_releases WHERE digest=? AND id NOT IN (SELECT id FROM digest_releases"
              + " WHERE digest=? ORDER BY created DESC LIMIT 3)",
          new Object[] {digest, digest});
      db.setTransactionSuccessful();
      return true;
    } finally {
      db.endTransaction();
    }
  }

  public boolean attachCover(String jobId, String articleId, String path, String key)
      throws Exception {
    return attachCoverKind(jobId, articleId, path, key, "generated", "");
  }

  public boolean attachCoverKind(
      String jobId, String articleId, String path, String key, String kind, String origin)
      throws Exception {
    db.beginTransaction();
    try {
      if (!current(jobId)) return false;
      db.execSQL(
          "UPDATE digest_jobs SET lease=? WHERE id=?",
          new Object[] {System.currentTimeMillis() + 900000, jobId});
      String releaseId = job(jobId).optString("cover_release", jobId);
      JSONObject release =
          one(
              "SELECT '{\"articles\":' || articles || '}' FROM digest_releases WHERE id=?",
              releaseId);
      if (release == null) return false;
      JSONArray list = release.getJSONArray("articles");
      boolean found = false;
      for (int i = 0; i < list.length(); i++) {
        JSONObject a = list.getJSONObject(i);
        if (a.getString("id").equals(articleId)) {
          a.put("image_path", path)
              .put("image_key", key)
              .put("image_kind", kind)
              .put("image_origin", origin);
          found = true;
        }
      }
      if (!found) return false;
      db.execSQL(
          "UPDATE digest_releases SET articles=? WHERE id=?",
          new Object[] {list.toString(), releaseId});
      db.setTransactionSuccessful();
      return true;
    } finally {
      db.endTransaction();
    }
  }

  public void invalidateAuthorizedJobs() {
    db.beginTransaction();
    try {
      db.execSQL("UPDATE digest_meta SET value=value+1 WHERE id='epoch'");
      db.execSQL(
          "UPDATE digest_jobs SET state='cancelled',reason='Вход изменился' WHERE state IN"
              + " ('queued','running','waiting','requires-login')");
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public JSONArray persistedArticles(String jobId) throws Exception {
    JSONObject snapshot = job(jobId);
    if (snapshot == null) return null;
    String release = snapshot.optString("cover_release", jobId);
    try (Cursor rows =
        db.rawQuery("SELECT articles FROM digest_releases WHERE id=?", new String[] {release})) {
      if (!rows.moveToFirst()) return null;
      JSONArray saved = new JSONArray(rows.getString(0));
      if (!snapshot.has("cover_article")) return saved;
      JSONArray selected = new JSONArray();
      for (int i = 0; i < saved.length(); i++)
        if (saved.getJSONObject(i).getString("id").equals(snapshot.getString("cover_article")))
          selected.put(saved.getJSONObject(i));
      return selected;
    }
  }

  public Set<String> referencedImages() throws Exception {
    return new DigestArchive(db).referencedImages();
  }

  public JSONArray cards(String digestId) throws Exception {
    return new DigestArchive(db).cards(digestId);
  }

  public JSONArray freshCards(long now) throws Exception {
    return new DigestArchive(db).freshCards(now);
  }

  public boolean hasPending() {
    return number("SELECT count(*) FROM digest_jobs WHERE state IN ('queued','waiting')") > 0;
  }

  public void resumeAuthorizedJobs() {
    db.execSQL("UPDATE digest_jobs SET state='queued' WHERE state='requires-login'");
  }

  public JSONArray dueSchedules(long now) throws Exception {
    return list("schedule", "");
  }

  public void recover(long now) {
    db.execSQL(
        "UPDATE digest_jobs SET state='waiting',lease=0 WHERE state='running' AND lease<?",
        new Object[] {now});
  }

  public JSONObject reserveGeneration(String jobId, String key, long now) throws Exception {
    db.beginTransaction();
    try {
      JSONObject prior =
          one(
              "SELECT '{\"operation_id\":\"' || id || '\",\"state\":\"' || state ||"
                  + " '\",\"path\":\"' || path || '\"}' FROM digest_generations WHERE key=?",
              key);
      if (prior != null) {
        if (!prior.optString("state").equals("ready")) prior.put("state", "unconfirmed");
        db.setTransactionSuccessful();
        return prior;
      }
      if (!current(jobId)) return new JSONObject().put("state", "cancelled");
      JSONObject schedule = job(jobId).getJSONObject("digest");
      String zone = schedule.optString("zone", "UTC");
      String day = Instant.ofEpochMilli(now).atZone(ZoneId.of(zone)).toLocalDate().toString();
      if (number("SELECT count(*) FROM digest_generations WHERE job=?", jobId) >= 6
          || number("SELECT count(*) FROM digest_generations WHERE day=?", day) >= 24)
        return new JSONObject().put("state", "limit");
      String id = UUID.randomUUID().toString();
      db.execSQL(
          "INSERT INTO digest_generations(id,job,key,state,day) VALUES (?,?,?,?,?)",
          new Object[] {id, jobId, key, "unconfirmed", day});
      db.setTransactionSuccessful();
      return new JSONObject().put("operation_id", id).put("state", "reserved");
    } finally {
      db.endTransaction();
    }
  }

  public void generationResult(String id, String state, String path, String model) {
    db.execSQL(
        "UPDATE digest_generations SET state=?,path=?,model=? WHERE id=? AND job IN (SELECT id FROM"
            + " digest_jobs WHERE state='running' AND epoch=(SELECT value FROM digest_meta WHERE"
            + " id='epoch'))",
        new Object[] {state, path, model, id});
  }

  public JSONArray prune(long now) {
    db.execSQL(
        "DELETE FROM digest_releases WHERE digest IN (SELECT id FROM digest_config WHERE"
            + " deleted=1)");
    return new JSONArray();
  }
}
