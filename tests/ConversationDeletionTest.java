package local.tvgpt.assistant;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import local.tvgpt.assistant.data.*;
import org.json.*;

/** Real SQLite checks, without touching the TV's actual conversation archive. */
public final class ConversationDeletionTest {
  public static void main(String[] args) throws Exception {
    Context c = new Context();
    SQLiteDatabase db = AppDatabase.get(c).getWritableDatabase();
    try {
      ConversationRepository repo = new ConversationRepository(c);
      for (int i = 0; i < 13; i++)
        repo.appendTurn("saved-" + i, "call", "one", 1000 + i, "user", "retained " + i);
      JSONArray first = repo.search("", 0, 0, "saved-12", 11, 0);
      JSONArray second = repo.search("", 0, 0, "saved-12", 11, 10);
      if (first.length() != 11
          || second.length() != 2
          || !first
              .getJSONObject(10)
              .getString("id")
              .equals(second.getJSONObject(0).getString("id")))
        throw new AssertionError("Bounded search pagination/lookahead lost records");
      String id = first.getJSONObject(0).getString("id");
      long rev = first.getJSONObject(0).getLong("revision");
      repo.appendTurn(id, "call", "two", 2000, "assistant", "new unseen turn");
      if (repo.deleteIfCurrent(id, rev, CancellationToken.NONE)
          || repo.turns(id, 10, 0).length() != 2)
        throw new AssertionError("Stale delete removed newly appended content");
      CancellationToken token = new CancellationToken();
      SQLiteDatabase.afterSql =
          sql -> {
            if (sql.startsWith("DELETE FROM turns")) token.cancel();
          };
      try {
        repo.deleteIfCurrent(id, rev + 1, token);
        throw new AssertionError("Cancellation after mutation committed");
      } catch (java.util.concurrent.CancellationException expected) {
      } finally {
        SQLiteDatabase.afterSql = null;
      }
      if (repo.get(id) == null || repo.turns(id, 10, 0).length() != 2)
        throw new AssertionError("Cancelled deletion did not roll back atomically");
      if (!repo.deleteIfCurrent(id, rev + 1, CancellationToken.NONE)
          || repo.get(id) != null
          || repo.turns(id, 10, 0).length() != 0)
        throw new AssertionError("Successful delete left readable turns");
      if (repo.appendTurn(id, "late", "three", 3000, "user", "must not resurrect")
          || repo.saveSummaryIfCurrent(id, rev + 1, "late", "must not resurrect")
          || repo.search("", 0, 0, "", 10).length() != 10)
        throw new AssertionError("Deletion tombstone lost");
      if (repo.get("saved-12") == null) throw new AssertionError("Unrelated archive deleted");
      System.out.println(
          "Conversation deletion: revision conflict, cancellation rollback, tombstone and"
              + " pagination passed");
    } finally {
      db.close();
    }
  }
}
