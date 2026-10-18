package local.tvgpt.assistant.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import local.tvgpt.assistant.CancellationToken;
import local.tvgpt.assistant.reminders.SchedulePolicy;

/** Revision-checked voice mutations share SQLite with the editor and scheduler. */
final class ReminderChanges {
  static boolean delete(SQLiteDatabase db, long id, long revision, CancellationToken token) {
    token.check();
    db.beginTransaction();
    try {
      token.check();
      boolean deleted =
          db.delete(
                  "reminders",
                  "id=? AND revision=? AND status!='delivering'",
                  new String[] {"" + id, "" + revision})
              == 1;
      token.check();
      db.setTransactionSuccessful();
      return deleted;
    } finally {
      db.endTransaction();
    }
  }

  static void timer(
      SQLiteDatabase db,
      long id,
      long revision,
      String body,
      long seconds,
      long wallNow,
      long elapsedNow,
      String boot,
      CancellationToken token) {
    token.check();
    if (body == null || body.trim().isEmpty() || body.length() > 1000 || seconds < 0)
      throw new IllegalArgumentException("Укажите текст и длительность таймера");
    long elapsed = seconds == 0 ? 0 : SchedulePolicy.timer(seconds, elapsedNow);
    db.beginTransaction();
    try {
      token.check();
      try (Cursor current =
          db.rawQuery(
              "SELECT id FROM reminders WHERE id=? AND revision=? AND kind='TIMER'"
                  + " AND enabled=1 AND status IN ('scheduled','pending')",
              new String[] {"" + id, "" + revision})) {
        if (!current.moveToFirst())
          throw new IllegalStateException("Таймер уже изменён или завершён");
      }
      ContentValues values = new ContentValues();
      values.put("body", body.trim());
      values.put("revision", revision + 1);
      if (seconds > 0) {
        long due = Math.addExact(wallNow, Math.multiplyExact(seconds, 1000));
        values.put("due", due);
        values.put("original_due", due);
        values.put("elapsed_due", elapsed);
        values.put("boot", boot);
        values.put("status", "scheduled");
      }
      token.check();
      if (db.update(
              "reminders",
              values,
              "id=? AND revision=? AND kind='TIMER' AND enabled=1 AND status IN"
                  + " ('scheduled','pending')",
              new String[] {"" + id, "" + revision})
          != 1) throw new IllegalStateException("Таймер уже изменён или выполняется");
      token.check();
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }
}
