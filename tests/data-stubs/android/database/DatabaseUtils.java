package android.database;

public final class DatabaseUtils {
  public static long longForQuery(
      android.database.sqlite.SQLiteDatabase db, String sql, String[] args) {
    try (Cursor c = db.rawQuery(sql, args)) {
      c.moveToFirst();
      return c.getLong(0);
    }
  }
}
