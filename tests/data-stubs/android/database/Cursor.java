package android.database;

import org.json.*;

public final class Cursor implements AutoCloseable {
  private final JSONArray rows;
  private final JSONArray columns;
  public static final int FIELD_TYPE_INTEGER = 1;
  private int at = -1;

  public Cursor(JSONArray rows, JSONArray columns) {
    this.rows = rows;
    this.columns = columns;
  }

  public int getColumnCount() {
    return columns.length();
  }

  public String getColumnName(int i) {
    return columns.getString(i);
  }

  public int getType(int i) {
    return rows.getJSONArray(at).opt(i) instanceof Number ? 1 : 3;
  }

  public boolean moveToFirst() {
    at = 0;
    return rows.length() > 0;
  }

  public boolean moveToNext() {
    at++;
    return at < rows.length();
  }

  public String getString(int column) {
    Object v = rows.getJSONArray(at).get(column);
    return v == JSONObject.NULL ? null : String.valueOf(v);
  }

  public long getLong(int column) {
    return rows.getJSONArray(at).getLong(column);
  }

  public void close() {}
}
