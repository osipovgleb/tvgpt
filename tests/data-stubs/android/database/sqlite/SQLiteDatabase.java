package android.database.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import java.io.*;
import java.util.*;
import org.json.*;

public final class SQLiteDatabase implements AutoCloseable {
  public static final int CONFLICT_REPLACE = 5, CONFLICT_IGNORE = 4;
  private final Process process;
  private final BufferedReader input;
  private final BufferedWriter output;
  private final Deque<Boolean> transactions = new ArrayDeque<>();
  private boolean rollback;
  public static volatile java.util.function.Consumer<String> afterSql;

  public SQLiteDatabase() {
    try {
      process =
          new ProcessBuilder(
                  "python3",
                  "tests/data-stubs/sqlite_bridge.py",
                  System.getProperty("digest.test.db", ":memory:"))
              .redirectError(ProcessBuilder.Redirect.INHERIT)
              .start();
      input = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
      output = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), "UTF-8"));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private synchronized JSONObject execute(String sql, Object[] args) {
    try {
      JSONArray values = new JSONArray();
      if (args != null) for (Object a : args) values.put(a);
      output.write(new JSONObject().put("sql", sql).put("args", values).toString());
      output.newLine();
      output.flush();
      JSONObject result = new JSONObject(input.readLine());
      if (result.has("error"))
        throw new IllegalStateException(result.getString("error") + " in " + sql);
      if (afterSql != null) afterSql.accept(sql);
      return result;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public void execSQL(String sql) {
    execute(sql, null);
  }

  public void execSQL(String sql, Object[] args) {
    execute(sql, args);
  }

  public Cursor rawQuery(String sql, String[] args) {
    JSONObject result = execute(sql, args);
    return new Cursor(result.getJSONArray("rows"), result.getJSONArray("columns"));
  }

  public long insertWithOnConflict(
      String table, String nullColumn, ContentValues values, int mode) {
    StringJoiner keys = new StringJoiner(","), slots = new StringJoiner(",");
    for (String key : values.keySet()) {
      keys.add(key);
      slots.add("?");
    }
    JSONObject result =
        execute(
            "INSERT OR "
                + (mode == CONFLICT_IGNORE ? "IGNORE" : "REPLACE")
                + " INTO "
                + table
                + " ("
                + keys
                + ") VALUES ("
                + slots
                + ")",
            values.values().toArray());
    return result.optInt("changes") == 0 ? -1 : result.optLong("id");
  }

  public long insertOrThrow(String table, String unused, ContentValues values) {
    StringJoiner keys = new StringJoiner(","), slots = new StringJoiner(",");
    for (String key : values.keySet()) {
      keys.add(key);
      slots.add("?");
    }
    return execute(
            "INSERT INTO " + table + " (" + keys + ") VALUES (" + slots + ")",
            values.values().toArray())
        .getLong("id");
  }

  public int update(String table, ContentValues values, String where, String[] args) {
    StringJoiner sets = new StringJoiner(",");
    java.util.List<Object> all = new ArrayList<>(values.values());
    for (String key : values.keySet()) sets.add(key + "=?");
    if (args != null) Collections.addAll(all, args);
    return execute("UPDATE " + table + " SET " + sets + " WHERE " + where, all.toArray())
        .getInt("changes");
  }

  public int delete(String table, String where, String[] args) {
    return execute("DELETE FROM " + table + " WHERE " + where, args).getInt("changes");
  }

  public SQLiteStatement compileStatement(String sql) {
    return new SQLiteStatement(this, sql);
  }

  int changes(String sql, Object[] args) {
    return execute(sql, args).getInt("changes");
  }

  public void beginTransaction() {
    if (transactions.isEmpty()) {
      execSQL("BEGIN IMMEDIATE");
      rollback = false;
    }
    transactions.push(false);
  }

  public void setTransactionSuccessful() {
    transactions.pop();
    transactions.push(true);
  }

  public void endTransaction() {
    if (!transactions.pop()) rollback = true;
    if (transactions.isEmpty()) execSQL(rollback ? "ROLLBACK" : "COMMIT");
  }

  public void setForeignKeyConstraintsEnabled(boolean on) {
    execSQL("PRAGMA foreign_keys=" + (on ? "ON" : "OFF"));
  }

  public void close() {
    try {
      output.close();
      process.waitFor();
    } catch (Exception e) {
      process.destroy();
    }
  }
}
