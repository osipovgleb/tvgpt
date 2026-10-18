package local.tvgpt.assistant.data;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.*;
import java.util.*;
import local.tvgpt.assistant.digests.*;
import org.json.*;

/** Read model shared by gallery, dream and cache admission. */
final class DigestArchive {
  private final SQLiteDatabase db;

  DigestArchive(SQLiteDatabase db) {
    this.db = db;
  }

  public Set<String> referencedImages() throws Exception {
    Set<String> paths = new HashSet<>();
    try (Cursor rows =
        db.rawQuery(
            "SELECT articles FROM digest_releases r JOIN digest_config d ON d.id=r.digest WHERE"
                + " d.deleted=0",
            null)) {
      while (rows.moveToNext()) {
        JSONArray articles = new JSONArray(rows.getString(0));
        for (int i = 0; i < articles.length(); i++) {
          String path = articles.getJSONObject(i).optString("image_path");
          if (!path.isEmpty()) paths.add(path);
        }
      }
    }
    return paths;
  }

  public JSONArray cards(String digestId) throws Exception {
    JSONArray out = new JSONArray();
    String sql =
        "SELECT r.digest,r.created,r.articles FROM digest_releases r JOIN digest_config d ON"
            + " d.id=r.digest WHERE d.deleted=0 AND d.kind='digest'";
    String[] args = null;
    if (digestId != null && !digestId.isEmpty()) {
      sql += " AND r.digest=?";
      args = new String[] {digestId};
    }
    try (Cursor c = db.rawQuery(sql + " ORDER BY r.created DESC LIMIT 60", args)) {
      while (c.moveToNext()) {
        JSONObject d = new DigestConfiguration(db).read("digest", c.getString(0));
        JSONArray a = new JSONArray(c.getString(2));
        for (int i = 0; i < a.length(); i++)
          out.put(
              a.getJSONObject(i)
                  .put("digest_id", c.getString(0))
                  .put("collected_at", c.getLong(1))
                  .put(
                      "screensaver",
                      d.optBoolean("screensaver", true) && d.optBoolean("enabled", true)));
      }
    }
    return out;
  }

  public JSONArray freshCards(long now) throws Exception {
    return DigestSlides.select(cards(""), now);
  }
}
