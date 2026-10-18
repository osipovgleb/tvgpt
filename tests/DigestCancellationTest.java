package local.tvgpt.assistant.digests;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.nio.file.Files;
import local.tvgpt.assistant.data.DigestRepository;
import org.json.*;

/** Cancellation admission tests use an independent SQLite fixture from archive coverage. */
public final class DigestCancellationTest {
  private static DigestRepository repo;
  private static Context context;

  private static String uuid() {
    return java.util.UUID.randomUUID().toString();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static String create() throws Exception {
    return repo.mutate(
            uuid(), "digest", new JSONObject().put("action", "create").put("title", "News"), 0)
        .getString("id");
  }

  private static void check(String name, DigestRepositoryTest.Check test) throws Exception {
    test.run();
    System.out.println("PASS " + name);
  }

  public static void main(String[] args) throws Exception {
    java.io.File root = Files.createTempDirectory("tvgpt-digest-cancel").toFile();
    System.setProperty("digest.test.root", root.toString());
    System.setProperty("digest.test.db", new java.io.File(root, "data.sqlite").toString());
    context = new Context();
    repo = new DigestRepository(context);
    check(
        "cancel after transaction wait prevents config mutation",
        () -> {
          local.tvgpt.assistant.CancellationToken token =
              new local.tvgpt.assistant.CancellationToken();
          DigestRepository scoped = new DigestRepository(context, token);
          int before = repo.list("digest", "").length();
          SQLiteDatabase.afterBegin = token::cancel;
          try {
            scoped.mutate(
                uuid(), "digest", new JSONObject().put("action", "create").put("title", "late"), 0);
            throw new AssertionError("cancelled config committed");
          } catch (java.util.concurrent.CancellationException expected) {
          } finally {
            SQLiteDatabase.afterBegin = null;
          }
          require(repo.list("digest", "").length() == before, "cancelled config persisted");
        });
    check(
        "cancel after initial config read rolls back",
        () -> {
          String id = create();
          long revision = repo.read("digest", id).getLong("revision");
          local.tvgpt.assistant.CancellationToken token =
              new local.tvgpt.assistant.CancellationToken();
          DigestRepository scoped = new DigestRepository(context, token);
          SQLiteDatabase.afterRead =
              sql -> {
                if (sql.contains("data FROM digest_config")) token.cancel();
              };
          try {
            scoped.mutate(
                uuid(), "digest", new JSONObject().put("action", "delete").put("id", id), revision);
            throw new AssertionError("cancelled delete committed");
          } catch (java.util.concurrent.CancellationException expected) {
          } finally {
            SQLiteDatabase.afterRead = null;
          }
          require(repo.read("digest", id) != null, "cancelled delete persisted");
        });
    check(
        "cancel after initial enqueue read prevents job admission",
        () -> {
          String id = create();
          local.tvgpt.assistant.CancellationToken token =
              new local.tvgpt.assistant.CancellationToken();
          DigestRepository scoped = new DigestRepository(context, token);
          SQLiteDatabase.afterRead =
              sql -> {
                if (sql.contains("data FROM digest_config")) token.cancel();
              };
          try {
            scoped.enqueue(id, uuid(), "", 0, 1000);
            throw new AssertionError("cancelled job admitted");
          } catch (java.util.concurrent.CancellationException expected) {
          } finally {
            SQLiteDatabase.afterRead = null;
          }
          require(repo.latestJob(id) == null, "cancelled job persisted");
        });
    check(
        "admitted job survives call cancellation",
        () -> {
          String id = create();
          local.tvgpt.assistant.CancellationToken token =
              new local.tvgpt.assistant.CancellationToken();
          DigestRepository scoped = new DigestRepository(context, token);
          JSONObject admitted = scoped.enqueue(id, uuid(), "", 0, 1000);
          token.cancel();
          require(
              repo.job(admitted.getString("job_id")).getString("state").equals("queued"),
              "call cancellation cancelled background job");
        });
    check(
        "cancel during cover release read rolls back nested admission",
        () -> {
          String id = create(), article = uuid();
          local.tvgpt.assistant.data.AppDatabase.get(context)
              .getWritableDatabase()
              .execSQL(
                  "INSERT INTO digest_releases(id,digest,created,articles) VALUES (?,?,?,?)",
                  new Object[] {
                    uuid(),
                    id,
                    1000,
                    new JSONArray().put(new JSONObject().put("id", article)).toString()
                  });
          local.tvgpt.assistant.CancellationToken token =
              new local.tvgpt.assistant.CancellationToken();
          DigestRepository scoped = new DigestRepository(context, token);
          SQLiteDatabase.afterRead =
              sql -> {
                if (sql.startsWith("SELECT id,articles FROM digest_releases")) token.cancel();
              };
          try {
            scoped.enqueueCover(id, uuid(), article, "cover");
            throw new AssertionError("cancelled cover job committed");
          } catch (java.util.concurrent.CancellationException expected) {
          } finally {
            SQLiteDatabase.afterRead = null;
          }
          require(repo.latestJob(id) == null, "nested cover admission survived cancellation");
        });
    System.out.println("DigestCancellationTest passed");
  }
}
