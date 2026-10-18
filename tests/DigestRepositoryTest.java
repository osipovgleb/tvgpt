package local.tvgpt.assistant.digests;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import local.tvgpt.assistant.data.*;
import org.json.*;

/** Production repository/config/schema SQL run against Python's actual SQLite engine. */
public final class DigestRepositoryTest {
  private static DigestRepository repo;
  private static Context context;
  private static int failures;

  interface Check {
    void run() throws Exception;
  }

  static void check(String name, Check test) {
    try {
      test.run();
      System.out.println("PASS " + name);
    } catch (Exception | AssertionError e) {
      failures++;
      System.out.println("FAIL " + name + ": " + e);
    }
  }

  static void require(boolean ok, String reason) {
    if (!ok) throw new AssertionError(reason);
  }

  static String uuid() {
    return UUID.randomUUID().toString();
  }

  static String create() throws Exception {
    return repo.mutate(
            uuid(),
            "digest",
            new JSONObject().put("action", "create").put("title", "News").put("zone", "UTC"),
            0)
        .getString("id");
  }

  static JSONObject article(String title) {
    return new JSONObject()
        .put("id", uuid())
        .put("title", title)
        .put("url", "https://example.org/" + uuid());
  }

  static JSONObject start(String digest) throws Exception {
    JSONObject j = repo.enqueue(digest, uuid(), "", 0, 1000);
    repo.claim(1000, 9999999999999L);
    return j;
  }

  public static void main(String[] args) throws Exception {
    File root = Files.createTempDirectory("tvgpt-digest-test").toFile();
    System.setProperty("digest.test.root", root.getAbsolutePath());
    System.setProperty("digest.test.db", new File(root, "data.sqlite").getAbsolutePath());
    try (SQLiteDatabase legacy = new SQLiteDatabase()) {
      legacy.execSQL("CREATE TABLE knowledge(id INTEGER PRIMARY KEY,text TEXT)");
      legacy.execSQL("INSERT INTO knowledge VALUES(1,'retained')");
      legacy.execSQL("PRAGMA user_version=2");
    }
    context = new Context();
    repo = new DigestRepository(context);
    check(
        "v2 migration retains legacy data",
        () -> {
          try (android.database.Cursor c =
              AppDatabase.get(context)
                  .getReadableDatabase()
                  .rawQuery("SELECT text FROM knowledge WHERE id=1", null)) {
            require(c.moveToFirst() && c.getString(0).equals("retained"), "lost legacy data");
          }
        });
    check(
        "operation id and revision guards",
        () -> {
          String op = uuid();
          JSONObject values = new JSONObject().put("action", "create").put("title", "idempotent");
          JSONObject first = repo.mutate(op, "digest", values, 0);
          require(
              first.toString().equals(repo.mutate(op, "digest", values, 0).toString()),
              "duplicate mutation");
          require(
              repo.mutate(
                      uuid(),
                      "digest",
                      new JSONObject().put("action", "update").put("id", first.getString("id")),
                      99)
                  .getString("status")
                  .equals("conflict"),
              "revision overwrite");
        });
    check(
        "unknown dates keep first discovery after repeat collection",
        () -> {
          String d = create();
          JSONObject a = article("unknown");
          JSONObject first = start(d);
          repo.publish(first.getString("job_id"), new JSONArray().put(a), 1000);
          repo.finish(first.getString("job_id"), "ready", "");
          JSONObject next = start(d);
          a.put("id", uuid());
          long later = 1000L + 172800001L;
          repo.publish(next.getString("job_id"), new JSONArray().put(a), later);
          repo.finish(next.getString("job_id"), "ready", "");
          require(
              DigestSlides.select(repo.cards(d), later).length() == 0,
              "recollection made an undated old story fresh again");
        });
    check(
        "new release replaces display and retention keeps three releases",
        () -> {
          String d = create();
          for (int i = 0; i < 4; i++) {
            JSONObject job = start(d);
            repo.publish(
                job.getString("job_id"), new JSONArray().put(article("edition" + i)), 2000 + i);
            repo.finish(job.getString("job_id"), "ready", "");
          }
          require(repo.cards(d).length() == 3, "archive did not retain exactly three editions");
          JSONArray visible = DigestSlides.latest(repo.cards(d));
          require(
              visible.length() == 1
                  && visible.getJSONObject(0).getString("title").equals("edition3"),
              "older editions mixed with the current gallery");
        });
    check(
        "cover jobs do not publish replacement news releases",
        () -> {
          String d = create();
          JSONObject j = start(d);
          JSONArray list = new JSONArray().put(article("one")).put(article("two"));
          repo.publish(j.getString("job_id"), list, 1000);
          repo.finish(j.getString("job_id"), "ready", "");
          for (int i = 0; i < 3; i++) {
            JSONObject cover =
                repo.enqueueCover(d, uuid(), list.getJSONObject(0).getString("id"), "new cover");
            repo.claim(1000, 9999999999999L);
            require(
                !repo.publish(
                    cover.getString("job_id"),
                    new JSONArray().put(list.getJSONObject(0)),
                    1001 + i),
                "cover publishes a full release");
            require(
                repo.attachCover(
                    cover.getString("job_id"),
                    list.getJSONObject(0).getString("id"),
                    "/photo",
                    "key"),
                "cannot attach in original release");
            repo.finish(cover.getString("job_id"), "ready", "");
          }
          require(repo.cards(d).length() == 2, "lost unrelated news");
        });
    check(
        "resume keeps persisted article identity",
        () -> {
          String d = create();
          JSONObject j = start(d);
          JSONArray articles = new JSONArray().put(article("stable"));
          repo.publish(j.getString("job_id"), articles, 1000);
          JSONArray recovered = repo.persistedArticles(j.getString("job_id"));
          require(
              recovered
                  .getJSONObject(0)
                  .getString("id")
                  .equals(articles.getJSONObject(0).getString("id")),
              "research repeated instead of persisted release");
          repo.finish(j.getString("job_id"), "ready", "");
        });
    check(
        "all permitted schedules participate",
        () -> {
          for (int n = 0; n < 6; n++) {
            String d = create();
            for (int i = 0; i < (n == 5 ? 1 : 20); i++)
              repo.mutate(
                  uuid(),
                  "schedule",
                  new JSONObject()
                      .put("action", "create")
                      .put("digest_id", d)
                      .put("repeat", "daily")
                      .put("zone", "UTC"),
                  0);
          }
          require(repo.dueSchedules(1000).length() == 101, "saved schedules silently missing");
        });
    check(
        "referenced cache survives pressure and rejects excess admission",
        () -> {
          String d = create();
          JSONObject j = start(d);
          DigestImages images = new DigestImages(context);
          File photo = images.file(DigestRunPolicy.coverKey("photo", "style"));
          Files.write(photo.toPath(), new byte[100]);
          JSONObject a = article("photo").put("image_path", photo.getAbsolutePath());
          repo.publish(j.getString("job_id"), new JSONArray().put(a), 1000);
          repo.finish(j.getString("job_id"), "ready", "");
          images.trim(50);
          require(photo.exists(), "evicted referenced image");
          File tmp = images.temporary("b");
          Files.write(tmp.toPath(), new byte[100]);
          require(
              !images.store(tmp, DigestRunPolicy.coverKey("new", "style"), 150L),
              "cache admission exceeded bound");
          tmp.delete();
        });
    check(
        "generation reservations enforce actual release and day budgets",
        () -> {
          for (int n = 0; n < 4; n++) {
            String d = create();
            JSONObject j = start(d);
            for (int i = 0; i < 6; i++)
              require(
                  repo.reserveGeneration(
                          j.getString("job_id"), DigestRunPolicy.coverKey(uuid(), "quota"), 1000)
                      .getString("state")
                      .equals("reserved"),
                  "premature limit");
            require(
                repo.reserveGeneration(
                        j.getString("job_id"), DigestRunPolicy.coverKey(uuid(), "quota"), 1000)
                    .getString("state")
                    .equals("limit"),
                "release quota bypass");
            repo.finish(j.getString("job_id"), "ready", "");
          }
          String d = create();
          JSONObject j = start(d);
          require(
              repo.reserveGeneration(
                      j.getString("job_id"), DigestRunPolicy.coverKey(uuid(), "quota"), 1000)
                  .getString("state")
                  .equals("limit"),
              "daily quota bypass");
          repo.finish(j.getString("job_id"), "ready", "");
        });
    check(
        "tombstone and logout forbid late publication",
        () -> {
          String d = create();
          JSONObject j = start(d);
          repo.mutate(uuid(), "digest", new JSONObject().put("action", "delete").put("id", d), 1);
          require(
              !repo.publish(j.getString("job_id"), new JSONArray().put(article("late")), 1000),
              "resurrected deleted digest");
          String another = create();
          JSONObject active = start(another);
          repo.invalidateAuthorizedJobs();
          require(
              !repo.publish(
                  active.getString("job_id"), new JSONArray().put(article("logout")), 1000),
              "logout late write");
        });
    if (failures > 0) throw new AssertionError(failures + " repository failures");
    System.out.println("DigestRepositoryTest passed");
  }
}
