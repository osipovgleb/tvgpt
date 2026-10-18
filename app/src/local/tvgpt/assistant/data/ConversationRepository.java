package local.tvgpt.assistant.data;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;

public final class ConversationRepository {
  private final SQLiteDatabase db;

  public ConversationRepository(Context c) {
    db = AppDatabase.get(c).getWritableDatabase();
  }

  public void ensure(String id, long started, long ended) {
    ContentValues v = new ContentValues();
    v.put("id", id);
    v.put("started", started);
    v.put("ended", ended);
    db.insertWithOnConflict("conversations", null, v, SQLiteDatabase.CONFLICT_IGNORE);
  }

  public void importLegacy(String id, long started, long ended, JSONArray transcript) {
    db.beginTransaction();
    try {
      ContentValues marker = new ContentValues();
      marker.put("workspace", id);
      if (db.insertWithOnConflict("imports", null, marker, SQLiteDatabase.CONFLICT_IGNORE) == -1)
        return;
      ensure(id, started, ended);
      for (int i = 0; i < transcript.length(); i++) {
        JSONObject t = transcript.optJSONObject(i);
        if (t != null)
          appendTurn(
              id,
              "legacy",
              "entry-" + i,
              t.optLong("at", 0),
              t.optString("role"),
              t.optString("text"));
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public boolean appendTurn(
      String id, String call, String event, long at, String role, String text) {
    if (text == null || text.isEmpty() || !(role.equals("user") || role.equals("assistant")))
      return false;
    db.beginTransaction();
    try {
      ensure(id, at, at);
      try (Cursor c =
          db.rawQuery("SELECT deleted FROM conversations WHERE id=?", new String[] {id})) {
        if (!c.moveToFirst() || c.getInt(0) != 0) return false;
      }
      ContentValues v = new ContentValues();
      v.put("conversation", id);
      v.put("event", DataPolicy.turnKey(call, event, System.nanoTime()));
      if (at > 0) v.put("at", at);
      v.put("role", role);
      v.put("text", text);
      boolean added =
          db.insertWithOnConflict("turns", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1;
      if (added)
        db.execSQL(
            "UPDATE conversations SET"
                + " ended=MAX(ended,?),revision=revision+1,summary_state='pending' WHERE id=?",
            new Object[] {at, id});
      db.setTransactionSuccessful();
      return added;
    } finally {
      db.endTransaction();
    }
  }

  private static String excerpt() {
    return "COALESCE((SELECT substr(text,1,160) FROM turns WHERE conversation=c.id AND role='user'"
        + " ORDER BY id LIMIT 1),(SELECT substr(text,1,160) FROM turns WHERE conversation=c.id"
        + " ORDER BY id LIMIT 1),'')";
  }

  public JSONArray recent(int limit, int offset) {
    return query(
        "SELECT c.*,"
            + excerpt()
            + " AS excerpt FROM conversations c WHERE deleted=0 AND revision>0"
            + " ORDER BY ended DESC LIMIT ? OFFSET ?",
        new String[] {"" + Math.max(1, Math.min(50, limit)), "" + Math.max(0, offset)});
  }

  public JSONArray search(String text, long since, long until, String current, int limit) {
    String bounded = text == null ? "" : text.substring(0, Math.min(200, text.length()));
    String pattern =
        "%" + bounded.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    return query(
        "SELECT c.id,c.started,c.ended,substr(c.title,1,160) AS title,"
            + "substr(c.summary,1,1000) AS summary,"
            + excerpt()
            + " AS excerpt"
            + " FROM conversations c WHERE c.deleted=0 AND c.revision>0 AND c.id<>?"
            + " AND (CAST(? AS INTEGER)=0 OR c.ended>=?) AND (CAST(? AS INTEGER)=0 OR c.started<=?)"
            + " AND (c.title LIKE ? ESCAPE '\\' OR c.summary LIKE ? ESCAPE '\\'"
            + " OR EXISTS(SELECT 1 FROM turns t WHERE t.conversation=c.id"
            + " AND t.text LIKE ? ESCAPE '\\')) ORDER BY c.ended DESC LIMIT ?",
        new String[] {
          current,
          "" + Math.max(0, since),
          "" + Math.max(0, since),
          "" + Math.max(0, until),
          "" + Math.max(0, until),
          pattern,
          pattern,
          pattern,
          "" + Math.max(1, Math.min(10, limit))
        });
  }

  public JSONArray turns(String id, int limit, int offset) {
    return query(
        "SELECT * FROM turns WHERE conversation=? ORDER BY id LIMIT ? OFFSET ?",
        new String[] {id, "" + Math.max(1, Math.min(100, limit)), "" + Math.max(0, offset)});
  }

  public JSONArray tail(String id, int limit) {
    return query(
        "SELECT * FROM (SELECT * FROM turns WHERE conversation=? ORDER BY id DESC LIMIT ?) ORDER BY"
            + " id",
        new String[] {id, "" + Math.max(1, Math.min(100, limit))});
  }

  public JSONObject get(String id) {
    JSONArray a = query("SELECT * FROM conversations WHERE id=? AND deleted=0", new String[] {id});
    return a.optJSONObject(0);
  }

  public void delete(String id) {
    db.beginTransaction();
    try {
      db.delete("turns", "conversation=?", new String[] {id});
      db.execSQL(
          "UPDATE conversations SET deleted=1,revision=revision+1,title='',summary='' WHERE id=?",
          new Object[] {id});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public boolean saveSummaryIfCurrent(String id, long revision, String title, String summary) {
    ContentValues v = new ContentValues();
    v.put("title", title);
    v.put("summary", summary);
    v.put("summary_revision", revision);
    v.put("summary_state", "ready");
    return db.update(
            "conversations",
            v,
            "id=? AND revision=? AND deleted=0",
            new String[] {id, "" + revision})
        == 1;
  }

  public void summaryFailed(String id, long revision) {
    ContentValues v = new ContentValues();
    v.put("summary_state", "failed");
    db.update(
        "conversations", v, "id=? AND revision=? AND deleted=0", new String[] {id, "" + revision});
  }

  private JSONArray query(String sql, String[] args) {
    JSONArray a = new JSONArray();
    try (Cursor c = db.rawQuery(sql, args)) {
      while (c.moveToNext()) {
        JSONObject v = new JSONObject();
        for (int i = 0; i < c.getColumnCount(); i++)
          try {
            v.put(
                c.getColumnName(i),
                c.isNull(i)
                    ? JSONObject.NULL
                    : c.getType(i) == Cursor.FIELD_TYPE_INTEGER ? c.getLong(i) : c.getString(i));
          } catch (JSONException e) {
            throw new IllegalStateException(e);
          }
        a.put(v);
      }
    }
    return a;
  }
}
