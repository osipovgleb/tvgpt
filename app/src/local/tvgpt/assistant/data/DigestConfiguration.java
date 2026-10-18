package local.tvgpt.assistant.data;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.*;
import java.util.*;
import local.tvgpt.assistant.CancellationToken;
import local.tvgpt.assistant.digests.*;
import org.json.*;

/** Configuration mutations and revision checks, independent of job execution. */
final class DigestConfiguration {
  private final SQLiteDatabase db;
  private final CancellationToken token;

  DigestConfiguration(SQLiteDatabase db) {
    this(db, CancellationToken.NONE);
  }

  DigestConfiguration(SQLiteDatabase db, CancellationToken token) {
    this.db = db;
    this.token = token;
  }

  private JSONObject one(String sql, String... args) throws Exception {
    try (Cursor c = db.rawQuery(sql, args)) {
      return c.moveToFirst() ? new JSONObject(c.getString(0)) : null;
    }
  }

  public JSONArray list(String kind, String parent) throws Exception {
    DigestPolicy.kind(kind);
    JSONArray result = new JSONArray();
    String sql = "SELECT data FROM digest_config WHERE kind=? AND deleted=0";
    String[] args = {kind};
    if (parent != null && !parent.isEmpty()) {
      sql += " AND parent=?";
      args = new String[] {kind, parent};
    }
    try (Cursor c =
        db.rawQuery(
            sql
                + " ORDER BY rowid DESC LIMIT "
                + (kind.equals("schedule") ? 400 : kind.equals("source") ? 600 : 20),
            args)) {
      while (c.moveToNext()) result.put(new JSONObject(c.getString(0)));
    }
    return result;
  }

  public JSONObject read(String kind, String id) throws Exception {
    DigestPolicy.kind(kind);
    DigestPolicy.id(id);
    return one("SELECT data FROM digest_config WHERE kind=? AND id=? AND deleted=0", kind, id);
  }

  public JSONObject mutate(String operationId, String kind, JSONObject values, long expected)
      throws Exception {
    DigestPolicy.id(operationId);
    DigestPolicy.kind(kind);
    token.check();
    db.beginTransaction();
    try {
      token.check();
      JSONObject previous = one("SELECT result FROM digest_ops WHERE id=?", operationId);
      if (previous != null) {
        db.setTransactionSuccessful();
        return previous;
      }
      String action = values.getString("action");
      if (!Arrays.asList("create", "update", "delete").contains(action))
        throw new IllegalArgumentException("Неизвестное изменение");
      String id =
          action.equals("create")
              ? UUID.randomUUID().toString()
              : DigestPolicy.id(values.getString("id"));
      JSONObject current = read(kind, id);
      if (!action.equals("create") && (current == null || current.optLong("revision") != expected))
        return new JSONObject().put("status", "conflict");
      JSONObject data = current == null ? new JSONObject() : new JSONObject(current.toString());
      Iterator<String> keys = values.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        if (!Arrays.asList("id", "revision", "action").contains(key))
          data.put(key, values.get(key));
      }
      String parent = data.optString("digest_id");
      if (!kind.equals("digest")) {
        DigestPolicy.id(parent);
        if (read("digest", parent) == null) throw new IllegalArgumentException("Подборка удалена");
      }
      if (action.equals("create"))
        DigestPolicy.capacity(
            list(kind, kind.equals("digest") ? "" : parent).length(),
            kind.equals("digest") ? 20 : kind.equals("source") ? 30 : 20);
      validate(kind, data, action);
      long revision = current == null ? 1 : expected + 1;
      data.put("id", id).put("revision", revision);
      if (current == null) data.put("created", System.currentTimeMillis());
      if (!data.has("enabled")) data.put("enabled", true);
      ContentValues row = new ContentValues();
      row.put("id", id);
      row.put("kind", kind);
      row.put("parent", parent);
      row.put("data", data.toString());
      row.put("revision", revision);
      row.put("deleted", action.equals("delete") ? 1 : 0);
      token.check();
      db.insertWithOnConflict("digest_config", null, row, SQLiteDatabase.CONFLICT_REPLACE);
      if (!kind.equals("schedule") || action.equals("delete")) {
        String digest = kind.equals("digest") ? id : parent;
        db.execSQL(
            "UPDATE digest_jobs SET state='cancelled',reason='Настройки изменились' WHERE digest=?"
                + " AND state IN ('queued','running','waiting','requires-login')",
            new Object[] {digest});
      }
      JSONObject result =
          new JSONObject()
              .put("id", id)
              .put("revision", revision)
              .put("status", action.equals("delete") ? "deleted" : "saved");
      db.execSQL(
          "INSERT INTO digest_ops(id,result) VALUES (?,?)",
          new Object[] {operationId, result.toString()});
      token.check();
      db.setTransactionSuccessful();
      return result;
    } finally {
      db.endTransaction();
    }
  }

  private void validate(String kind, JSONObject data, String action) throws Exception {
    if (action.equals("delete")) return;
    data.put("title", DigestPolicy.text(data, "title", 120));
    if (kind.equals("digest")) {
      data.put("topics", DigestPolicy.text(data, "topics", 1000));
      if (!Arrays.asList("open", "selected").contains(data.optString("source_mode", "open")))
        throw new IllegalArgumentException("Режим источников");
    }
    if (kind.equals("source")) {
      String url = DigestPolicy.text(data, "url", 2048);
      java.net.URI uri = new java.net.URI(url);
      if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
        throw new IllegalArgumentException("Источник должен иметь HTTPS адрес");
      if (!Arrays.asList("rss", "atom", "web").contains(data.optString("type", "rss")))
        throw new IllegalArgumentException("Тип источника");
      data.put("url", url);
    }
    if (kind.equals("schedule")) DigestSchedulePolicy.next(data, System.currentTimeMillis());
  }
}
