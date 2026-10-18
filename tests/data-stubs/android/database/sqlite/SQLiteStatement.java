package android.database.sqlite;

public final class SQLiteStatement implements AutoCloseable {
  private final SQLiteDatabase db;
  private final String sql;
  private long id;

  SQLiteStatement(SQLiteDatabase db, String sql) {
    this.db = db;
    this.sql = sql;
  }

  public void bindLong(int i, long n) {
    id = n;
  }

  public int executeUpdateDelete() {
    return db.changes(sql, new Object[] {id});
  }

  public void close() {}
}
