package android.database;

import org.json.*;

public final class Cursor implements AutoCloseable {
  private final JSONArray rows;
  private int at = -1;

  public Cursor(JSONArray rows) {
    this.rows = rows;
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
