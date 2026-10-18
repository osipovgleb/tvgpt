package local.tvgpt.assistant.data;

import android.content.Context;
import android.database.sqlite.*;

public final class AppDatabase extends SQLiteOpenHelper {
  private static AppDatabase instance;

  public static synchronized AppDatabase get(Context c) {
    if (instance == null) instance = new AppDatabase(c.getApplicationContext());
    return instance;
  }

  private AppDatabase(Context c) {
    super(c, "assistant.db", null, 2);
    setWriteAheadLoggingEnabled(true);
  }

  @Override
  public void onConfigure(SQLiteDatabase db) {
    db.setForeignKeyConstraintsEnabled(true);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE imports (workspace TEXT PRIMARY KEY)");
    db.execSQL(
        "CREATE TABLE conversations (id TEXT PRIMARY KEY, started INTEGER NOT NULL, ended INTEGER"
            + " NOT NULL, revision INTEGER NOT NULL DEFAULT 0, title TEXT NOT NULL DEFAULT '',"
            + " summary TEXT NOT NULL DEFAULT '', summary_revision INTEGER NOT NULL DEFAULT -1,"
            + " summary_state TEXT NOT NULL DEFAULT 'pending', deleted INTEGER NOT NULL DEFAULT"
            + " 0)");
    db.execSQL(
        "CREATE TABLE turns (id INTEGER PRIMARY KEY AUTOINCREMENT, conversation TEXT NOT NULL"
            + " REFERENCES conversations(id) ON DELETE CASCADE, event TEXT NOT NULL, at INTEGER,"
            + " role TEXT NOT NULL, text TEXT NOT NULL, UNIQUE(conversation,event))");
    db.execSQL("CREATE INDEX turns_conversation ON turns(conversation,id)");
    db.execSQL(
        "CREATE TABLE knowledge (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, text"
            + " TEXT NOT NULL, updated INTEGER NOT NULL, source TEXT NOT NULL, revision INTEGER NOT"
            + " NULL)");
    db.execSQL(
        "CREATE TABLE reminders (id INTEGER PRIMARY KEY AUTOINCREMENT, body TEXT NOT NULL, due"
            + " INTEGER NOT NULL, zone TEXT NOT NULL, kind TEXT NOT NULL, repeat TEXT NOT NULL,"
            + " weekdays INTEGER NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, status TEXT NOT NULL"
            + " DEFAULT 'scheduled', elapsed_due INTEGER NOT NULL DEFAULT 0, boot TEXT NOT NULL"
            + " DEFAULT '', action TEXT NOT NULL DEFAULT '', original_due INTEGER NOT NULL,"
            + " revision INTEGER NOT NULL DEFAULT 1, last_status TEXT NOT NULL DEFAULT '',"
            + " last_delivery INTEGER NOT NULL DEFAULT 0)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    if (oldVersion < 2) {
      db.execSQL("CREATE TABLE IF NOT EXISTS imports (workspace TEXT PRIMARY KEY)");
      db.execSQL("ALTER TABLE reminders ADD COLUMN last_status TEXT NOT NULL DEFAULT ''");
      db.execSQL("ALTER TABLE reminders ADD COLUMN last_delivery INTEGER NOT NULL DEFAULT 0");
    }
    if (newVersion > 2) throw new IllegalStateException("Unknown database migration");
  }
}
