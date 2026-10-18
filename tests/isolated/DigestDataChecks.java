package local.tvgpt.assistant;

import android.content.Context;
import android.os.Bundle;
import java.util.*;
import local.tvgpt.assistant.data.*;
import org.json.*;

/** Runs in the self-targeted data package, never in the working assistant. */
public final class DigestDataChecks {
  private static void require(boolean ok, String label) {
    if (!ok) throw new AssertionError(label);
  }

  public static void run(Context c, Bundle out) throws Exception {
    legacyDatabase(c);
    DigestRepository repo = new DigestRepository(c);
    try (android.database.Cursor legacy =
        AppDatabase.get(c)
            .getReadableDatabase()
            .rawQuery("SELECT text FROM knowledge WHERE title='legacy-sentinel'", null)) {
      require(
          legacy.moveToFirst() && legacy.getString(0).equals("retained"),
          "migration lost existing data");
    }
    long now = System.currentTimeMillis();
    String operation = UUID.randomUUID().toString();
    JSONObject create =
        new JSONObject()
            .put("action", "create")
            .put("title", "Test digest")
            .put("topics", "space")
            .put("zone", "UTC");
    JSONObject made = repo.mutate(operation, "digest", create, 0),
        again = repo.mutate(operation, "digest", create, 0);
    String id = made.getString("id");
    require(id.equals(again.getString("id")), "duplicate operation");
    JSONObject job = repo.enqueue(id, UUID.randomUUID().toString(), "", 0, now);
    String jobId = job.getString("job_id");
    require(repo.claim(now, now + 10000) != null, "claim");
    require(repo.claim(now, now + 10000) == null, "double claim");
    JSONArray articles =
        new JSONArray()
            .put(
                new JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("title", "News")
                    .put("url", "https://example.org/a")
                    .put(
                        "sources",
                        new JSONArray().put(new JSONObject().put("url", "https://example.org/a"))));
    require(repo.publish(jobId, articles, now), "initial publication");
    require(repo.cards(id).getJSONObject(0).isNull("published_at"), "invented date");
    JSONObject wrong =
        repo.mutate(
            UUID.randomUUID().toString(),
            "digest",
            new JSONObject().put("action", "update").put("id", id).put("title", "wrong"),
            99);
    require(wrong.getString("status").equals("conflict"), "lost revision guard");
    String generationKey =
        UUID.randomUUID().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", "");
    JSONObject reserve = repo.reserveGeneration(jobId, generationKey, now);
    require(reserve.getString("state").equals("reserved"), "reservation");
    require(
        repo.reserveGeneration(jobId, generationKey, now).getString("state").equals("unconfirmed"),
        "blind generation retry");
    repo.mutate(
        UUID.randomUUID().toString(),
        "digest",
        new JSONObject().put("action", "delete").put("id", id),
        1);
    require(!repo.publish(jobId, articles, now), "deleted digest resurrection");
    JSONObject another = repo.mutate(UUID.randomUUID().toString(), "digest", create, 0);
    JSONObject second =
        repo.enqueue(another.getString("id"), UUID.randomUUID().toString(), "", 0, now);
    repo.claim(now, now + 10000);
    repo.invalidateAuthorizedJobs();
    require(!repo.publish(second.getString("job_id"), articles, now), "logout late write");
    repo.mutate(
        UUID.randomUUID().toString(),
        "digest",
        new JSONObject().put("action", "delete").put("id", another.getString("id")),
        1);
    out.putBoolean("digests_idempotency_revisions_claims_tombstones_logout", true);
  }

  private static void legacyDatabase(Context c) {
    c.deleteDatabase("assistant.db");
    android.database.sqlite.SQLiteOpenHelper legacy =
        new android.database.sqlite.SQLiteOpenHelper(c, "assistant.db", null, 2) {
          public void onCreate(android.database.sqlite.SQLiteDatabase db) {

            db.execSQL("CREATE TABLE imports (workspace TEXT PRIMARY KEY)");
            db.execSQL(
                "CREATE TABLE conversations (id TEXT PRIMARY KEY, started INTEGER NOT NULL, ended"
                    + " INTEGER NOT NULL, revision INTEGER NOT NULL DEFAULT 0, title TEXT NOT NULL"
                    + " DEFAULT '', summary TEXT NOT NULL DEFAULT '', summary_revision INTEGER NOT"
                    + " NULL DEFAULT -1, summary_state TEXT NOT NULL DEFAULT 'pending', deleted"
                    + " INTEGER NOT NULL DEFAULT 0)");
            db.execSQL(
                "CREATE TABLE turns (id INTEGER PRIMARY KEY AUTOINCREMENT, conversation TEXT NOT"
                    + " NULL REFERENCES conversations(id) ON DELETE CASCADE, event TEXT NOT NULL,"
                    + " at INTEGER, role TEXT NOT NULL, text TEXT NOT NULL,"
                    + " UNIQUE(conversation,event))");
            db.execSQL("CREATE INDEX turns_conversation ON turns(conversation,id)");
            db.execSQL(
                "CREATE TABLE knowledge (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL,"
                    + " text TEXT NOT NULL, updated INTEGER NOT NULL, source TEXT NOT NULL,"
                    + " revision INTEGER NOT NULL)");
            db.execSQL(
                "CREATE TABLE reminders (id INTEGER PRIMARY KEY AUTOINCREMENT, body TEXT NOT NULL,"
                    + " due INTEGER NOT NULL, zone TEXT NOT NULL, kind TEXT NOT NULL, repeat TEXT"
                    + " NOT NULL, weekdays INTEGER NOT NULL, enabled INTEGER NOT NULL DEFAULT 1,"
                    + " status TEXT NOT NULL DEFAULT 'scheduled', elapsed_due INTEGER NOT NULL"
                    + " DEFAULT 0, boot TEXT NOT NULL DEFAULT '', action TEXT NOT NULL DEFAULT '',"
                    + " original_due INTEGER NOT NULL, revision INTEGER NOT NULL DEFAULT 1,"
                    + " last_status TEXT NOT NULL DEFAULT '', last_delivery INTEGER NOT NULL"
                    + " DEFAULT 0)");

            db.execSQL(
                "INSERT INTO knowledge(title,text,updated,source,revision) VALUES"
                    + " ('legacy-sentinel','retained',1,'test',1)");
          }

          public void onUpgrade(
              android.database.sqlite.SQLiteDatabase db, int oldVersion, int newVersion) {
            throw new AssertionError("Fixture upgrade");
          }
        };
    legacy.getWritableDatabase();
    legacy.close();
  }
}
