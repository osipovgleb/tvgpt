package local.tvgpt.assistant.data;

import android.database.sqlite.SQLiteDatabase;

/** Additive schema; JSON payloads are bounded by DigestPolicy and versioned per entity. */
final class DigestSchema {
  static void create(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS digest_config (id TEXT PRIMARY KEY, kind TEXT NOT NULL, parent"
            + " TEXT NOT NULL DEFAULT '', data TEXT NOT NULL, revision INTEGER NOT NULL, deleted"
            + " INTEGER NOT NULL DEFAULT 0)");
    db.execSQL("CREATE INDEX IF NOT EXISTS digest_parent ON digest_config(kind,parent,deleted)");
    db.execSQL("CREATE TABLE IF NOT EXISTS digest_ops (id TEXT PRIMARY KEY, result TEXT NOT NULL)");
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS digest_jobs (id TEXT PRIMARY KEY, digest TEXT NOT NULL,"
            + " operation TEXT NOT NULL UNIQUE, snapshot TEXT NOT NULL, state TEXT NOT NULL, reason"
            + " TEXT NOT NULL DEFAULT '', lease INTEGER NOT NULL DEFAULT 0, created INTEGER NOT"
            + " NULL, epoch INTEGER NOT NULL)");
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS digest_releases (id TEXT PRIMARY KEY, digest TEXT NOT NULL,"
            + " created INTEGER NOT NULL, articles TEXT NOT NULL)");
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS digest_generations (id TEXT PRIMARY KEY, job TEXT NOT NULL, key"
            + " TEXT NOT NULL UNIQUE, state TEXT NOT NULL, path TEXT NOT NULL DEFAULT '', model"
            + " TEXT NOT NULL DEFAULT '', day TEXT NOT NULL)");
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS digest_meta (id TEXT PRIMARY KEY, value INTEGER NOT NULL)");
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS digest_seen (url TEXT PRIMARY KEY, discovered INTEGER NOT"
            + " NULL)");
    db.execSQL("INSERT OR IGNORE INTO digest_meta VALUES ('epoch',0)");
  }
}
