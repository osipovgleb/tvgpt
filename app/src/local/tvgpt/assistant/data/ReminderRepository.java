package local.tvgpt.assistant.data;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.time.*;
import local.tvgpt.assistant.CancellationToken;
import local.tvgpt.assistant.reminders.SchedulePolicy;
import org.json.*;

public final class ReminderRepository {
  private final SQLiteDatabase db;

  public ReminderRepository(Context c) {
    db = AppDatabase.get(c).getWritableDatabase();
  }

  public long create(
      String body,
      long due,
      String zone,
      String kind,
      String repeat,
      int days,
      long elapsed,
      String boot,
      String action) {
    return create(
        body, due, zone, kind, repeat, days, elapsed, boot, action, CancellationToken.NONE);
  }

  public long create(
      String body,
      long due,
      String zone,
      String kind,
      String repeat,
      int days,
      long elapsed,
      String boot,
      String action,
      CancellationToken token) {
    token.check();
    if (body == null || body.trim().isEmpty() || body.length() > 1000)
      throw new IllegalArgumentException("Текст: от 1 до 1000 символов");
    ZoneId.of(zone);
    if (!java.util.Arrays.asList("REMINDER", "TIMER", "ACTION").contains(kind))
      throw new IllegalArgumentException("Неизвестный тип");
    if (!java.util.Arrays.asList("NONE", "DAILY", "WEEKLY").contains(repeat))
      throw new IllegalArgumentException("Неизвестный повтор");
    if (due <= System.currentTimeMillis())
      throw new IllegalArgumentException("Укажите время в будущем");
    if (!repeat.equals("NONE")) SchedulePolicy.next(due, repeat, days, ZoneId.of(zone), due);
    if (kind.equals("TIMER") && !repeat.equals("NONE"))
      throw new IllegalArgumentException("Таймер не повторяется");
    ContentValues v = new ContentValues();
    v.put("body", body.trim());
    v.put("due", due);
    v.put("original_due", due);
    v.put("zone", zone);
    v.put("kind", kind);
    v.put("repeat", repeat);
    v.put("weekdays", days);
    v.put("elapsed_due", elapsed);
    v.put("boot", boot);
    v.put("action", action);
    db.beginTransaction();
    try {
      token.check();
      long id = db.insertOrThrow("reminders", null, v);
      token.check();
      db.setTransactionSuccessful();
      return id;
    } finally {
      db.endTransaction();
    }
  }

  public JSONArray list() {
    return query("SELECT * FROM reminders ORDER BY enabled DESC,due LIMIT 200", null);
  }

  public JSONArray pending(long now) {
    return query(
        "SELECT * FROM reminders WHERE enabled=1 AND status IN ('scheduled','pending') AND due<=?"
            + " ORDER BY due LIMIT 200",
        new String[] {"" + now});
  }

  public void deferDue(long now) {
    db.execSQL(
        "UPDATE reminders SET status='pending' WHERE enabled=1 AND status='scheduled' AND due<=?",
        new Object[] {now});
  }

  public boolean claim(long id) {
    try (Cursor c =
        db.rawQuery("SELECT revision FROM reminders WHERE id=?", new String[] {"" + id})) {
      return c.moveToFirst() && claim(id, c.getLong(0));
    }
  }

  /** Claim only the exact snapshot selected for this delivery batch. */
  public boolean claim(long id, long expectedRevision) {
    ContentValues v = new ContentValues();
    v.put("status", "delivering");
    return db.update(
            "reminders",
            v,
            "id=? AND enabled=1 AND status IN ('pending','scheduled') AND due<=?"
                + " AND revision=?",
            new String[] {"" + id, "" + System.currentTimeMillis(), "" + expectedRevision})
        == 1;
  }

  public void finish(JSONObject item, boolean success) {
    finish(item, success ? "completed" : "failed");
  }

  public void finish(JSONObject item, String outcome) {
    if (!java.util.Arrays.asList("completed", "failed", "unconfirmed", "notified")
        .contains(outcome)) throw new IllegalArgumentException("outcome");
    long id = item.optLong("id");
    String repeat = item.optString("repeat");
    db.execSQL(
        "UPDATE reminders SET last_status=?,last_delivery=? WHERE id=? AND status='delivering'",
        new Object[] {outcome, System.currentTimeMillis(), id});
    if (!repeat.equals("NONE")) {
      long next =
          SchedulePolicy.next(
              item.optLong("original_due"),
              repeat,
              item.optInt("weekdays"),
              ZoneId.of(item.optString("zone")),
              System.currentTimeMillis());
      db.execSQL(
          "UPDATE reminders SET status='scheduled',due=?,revision=revision+1 WHERE id=? AND"
              + " enabled=1 AND status='delivering'",
          new Object[] {next, id});
    } else
      db.execSQL(
          "UPDATE reminders SET status=?,enabled=0,revision=revision+1 WHERE id=? AND"
              + " status='delivering'",
          new Object[] {outcome, id});
  }

  public void edit(
      long id, long revision, String body, long due, String zone, String repeat, int days) {
    edit(id, revision, body, due, zone, repeat, days, CancellationToken.NONE);
  }

  public void edit(
      long id,
      long revision,
      String body,
      long due,
      String zone,
      String repeat,
      int days,
      CancellationToken token) {
    token.check();
    if (body == null
        || body.trim().isEmpty()
        || body.length() > 1000
        || due <= System.currentTimeMillis())
      throw new IllegalArgumentException("Укажите текст и будущее время");
    ZoneId z = ZoneId.of(zone);
    if (!repeat.equals("NONE")) SchedulePolicy.next(due, repeat, days, z, due);
    ContentValues v = new ContentValues();
    v.put("body", body);
    v.put("due", due);
    v.put("original_due", due);
    v.put("zone", zone);
    v.put("repeat", repeat);
    v.put("weekdays", days);
    v.put("status", "scheduled");
    v.put("enabled", 1);
    db.beginTransaction();
    try {
      token.check();
      try (Cursor current =
          db.rawQuery(
              "SELECT kind FROM reminders WHERE id=? AND revision=?",
              new String[] {"" + id, "" + revision})) {
        if (!current.moveToFirst())
          throw new IllegalStateException("Напоминание уже изменено. Откройте список снова.");
        String kind = current.getString(0);
        v.put("kind", kind.equals("TIMER") ? "REMINDER" : kind);
      }
      v.put("elapsed_due", 0);
      v.put("revision", revision + 1);
      token.check();
      if (db.update(
              "reminders",
              v,
              "id=? AND revision=? AND status!='delivering'",
              new String[] {"" + id, "" + revision})
          != 1) throw new IllegalStateException("Напоминание уже изменено. Откройте список снова.");
      token.check();
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public boolean cancel(long id) {
    return cancel(id, CancellationToken.NONE);
  }

  public boolean cancel(long id, CancellationToken token) {
    token.check();
    db.beginTransaction();
    try {
      token.check();
      try (SQLiteStatement statement =
          db.compileStatement(
              "UPDATE reminders SET enabled=0,status='cancelled',revision=revision+1 WHERE id=? AND"
                  + " enabled=1 AND status IN ('scheduled','pending')")) {
        statement.bindLong(1, id);
        token.check();
        boolean cancelled = statement.executeUpdateDelete() == 1;
        token.check();
        db.setTransactionSuccessful();
        return cancelled;
      }
    } finally {
      db.endTransaction();
    }
  }

  public void delete(long id) {
    delete(id, CancellationToken.NONE);
  }

  public void delete(long id, CancellationToken token) {
    token.check();
    db.beginTransaction();
    try {
      token.check();
      try (Cursor c =
          db.rawQuery("SELECT status FROM reminders WHERE id=?", new String[] {"" + id})) {
        if (c.moveToFirst() && c.getString(0).equals("delivering"))
          throw new IllegalStateException("Напоминание уже выполняется. Дождитесь результата.");
      }
      token.check();
      db.delete("reminders", "id=?", new String[] {"" + id});
      token.check();
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void recoverInterrupted() {
    db.beginTransaction();
    try {
      JSONArray abandoned = query("SELECT * FROM reminders WHERE status='delivering'", null);
      for (int i = 0; i < abandoned.length(); i++)
        finish(abandoned.optJSONObject(i), "unconfirmed");
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /** Claim is the durable dispatch boundary: later cancellation cannot promise prevention. */
  public String cancelOutcome(long id) {
    return cancelOutcome(id, CancellationToken.NONE);
  }

  public String cancelOutcome(long id, CancellationToken token) {
    if (cancel(id, token)) return "cancelled";
    try (Cursor c =
        db.rawQuery("SELECT status FROM reminders WHERE id=?", new String[] {"" + id})) {
      token.check();
      return c.moveToFirst() && c.getString(0).equals("delivering")
          ? "already_dispatching_unconfirmed"
          : "not_active";
    }
  }

  public void rebaseTimers(long now, long elapsed, String boot) {
    db.execSQL(
        "UPDATE reminders SET due=?+MAX(0,elapsed_due-?) WHERE enabled=1"
            + " AND status='scheduled' AND kind='TIMER' AND boot=?",
        new Object[] {now, elapsed, boot});
  }

  /** SQL aggregate avoids the presentation list's 200-row cap. Pending work waits for screen-on. */
  public long nextDue(boolean interactive) {
    try (Cursor c =
        db.rawQuery(
            "SELECT MIN(due) FROM reminders WHERE enabled=1 AND "
                + (interactive ? "status IN ('scheduled','pending')" : "status='scheduled'"),
            null)) {
      if (!c.moveToFirst() || c.getString(0) == null) return Long.MAX_VALUE;
      return c.getLong(0);
    }
  }

  public void timerDue(long id, long due) {
    db.execSQL(
        "UPDATE reminders SET due=? WHERE id=? AND enabled=1 AND status='scheduled'",
        new Object[] {due, id});
  }

  private JSONArray query(String sql, String[] args) {
    JSONArray out = new JSONArray();
    try (Cursor c = db.rawQuery(sql, args)) {
      while (c.moveToNext()) {
        JSONObject item = new JSONObject();
        for (int i = 0; i < c.getColumnCount(); i++)
          try {
            item.put(
                c.getColumnName(i),
                c.getType(i) == Cursor.FIELD_TYPE_INTEGER ? c.getLong(i) : c.getString(i));
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        out.put(item);
      }
    }
    return out;
  }
}
