package local.tvgpt.assistant.data;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.time.*;
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
    return db.insertOrThrow("reminders", null, v);
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
    ContentValues v = new ContentValues();
    v.put("status", "delivering");
    return db.update(
            "reminders",
            v,
            "id=? AND enabled=1 AND status IN ('pending','scheduled') AND due<=?",
            new String[] {"" + id, "" + System.currentTimeMillis()})
        == 1;
  }

  public void finish(JSONObject item, boolean success) {
    finish(item, success ? "completed" : "failed");
  }

  public void finish(JSONObject item, String outcome) {
    if (!outcome.equals("completed") && !outcome.equals("failed") && !outcome.equals("unconfirmed"))
      throw new IllegalArgumentException("outcome");
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
    if (db.update(
            "reminders",
            v,
            "id=? AND revision=? AND status!='delivering'",
            new String[] {"" + id, "" + revision})
        != 1) throw new IllegalStateException("Напоминание уже изменено. Откройте список снова.");
  }

  public boolean cancel(long id) {
    try (android.database.sqlite.SQLiteStatement statement =
        db.compileStatement(
            "UPDATE reminders SET enabled=0,status='cancelled',revision=revision+1 WHERE id=? AND"
                + " enabled=1")) {
      statement.bindLong(1, id);
      return statement.executeUpdateDelete() == 1;
    }
  }

  public void delete(long id) {
    db.delete("reminders", "id=?", new String[] {"" + id});
  }

  public void recoverInterrupted() {
    db.execSQL(
        "UPDATE reminders SET status='failed',enabled=0,revision=revision+1 WHERE"
            + " status='delivering'");
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
