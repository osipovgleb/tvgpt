package android.database.sqlite;

import android.content.Context;
import android.database.Cursor;

public abstract class SQLiteOpenHelper {
  private final int version;
  private SQLiteDatabase db;

  public SQLiteOpenHelper(Context c, String name, Object factory, int version) {
    this.version = version;
  }

  public synchronized SQLiteDatabase getWritableDatabase() {
    if (db == null) {
      db = new SQLiteDatabase();
      onConfigure(db);
      try (Cursor cursor = db.rawQuery("PRAGMA user_version", null)) {
        cursor.moveToFirst();
        int old = (int) cursor.getLong(0);
        if (old == 0) onCreate(db);
        else if (old < version) onUpgrade(db, old, version);
      }
      db.execSQL("PRAGMA user_version=" + version);
    }
    return db;
  }

  public SQLiteDatabase getReadableDatabase() {
    return getWritableDatabase();
  }

  public void setWriteAheadLoggingEnabled(boolean enabled) {}

  public void onConfigure(SQLiteDatabase db) {}

  public abstract void onCreate(SQLiteDatabase db);

  public abstract void onUpgrade(SQLiteDatabase db, int old, int version);
}
