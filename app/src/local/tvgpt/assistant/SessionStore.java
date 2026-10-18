package local.tvgpt.assistant;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import org.json.*;

/**
 * One fresh working conversation per invocation; completed conversations are archived separately.
 */
final class SessionStore {

  static final class State {
    String id;
    long created, lastUsed;
    JSONArray history, transcript;
    final Object execution = new Object();
    SessionStore owner;
  }

  private final File directory;
  private State current;

  interface Archive {
    void accept(State state) throws Exception;
  }

  private final Archive archive;

  SessionStore(File directory) {
    this(directory, null);
  }

  SessionStore(File directory, Archive archive) {
    this.directory = directory;
    this.archive = archive;
  }

  synchronized State open(long now) throws Exception {
    if (current == null && new File(directory, "session.json").isFile()) {
      JSONObject json =
          new JSONObject(
              new String(
                  Files.readAllBytes(new File(directory, "session.json").toPath()), "UTF-8"));
      current = new State();
      current.id = json.getString("id");
      current.created = json.getLong("created");
      current.lastUsed = json.getLong("last_used");
      current.history = json.getJSONArray("history");
      current.transcript = json.optJSONArray("transcript");
      if (current.transcript == null) current.transcript = new JSONArray();
    }
    if (current != null && archive != null) {
      synchronized (current) {
        archive.accept(current);
      }
    }
    current = new State();
    current.id = UUID.randomUUID().toString();
    current.created = now;
    current.history = new JSONArray();
    current.transcript = new JSONArray();
    current.owner = this;
    current.lastUsed = now;
    save(current);
    return current;
  }

  // Keep two fresh views for comparisons; older results retain receipts, not image payloads.
  static void retainRecentVisuals(JSONArray history) throws Exception {
    int kept = 0;
    for (int i = history.length() - 1; i >= 0; i--) {
      JSONObject item = history.optJSONObject(i);
      if (item == null || !(item.opt("output") instanceof JSONArray)) continue;
      if (++kept > 2) item.put("output", textEvidence(item.getJSONArray("output")));
    }
  }

  private static String textEvidence(JSONArray content) {
    StringBuilder receipt = new StringBuilder();
    for (int j = 0; j < content.length() && receipt.length() < 8192; j++) {
      JSONObject part = content.optJSONObject(j);
      if (part == null || !part.optString("type").equals("input_text")) continue;
      if (receipt.length() > 0) receipt.append('\n');
      String text = part.optString("text");
      receipt.append(text, 0, Math.min(text.length(), 8192 - receipt.length()));
    }
    return receipt.length() == 0 ? "[Снимок экрана предыдущего действия]" : receipt.toString();
  }

  synchronized void save(State state) throws Exception {
    if (state != current) return;
    synchronized (state) {
      // Archival can run during Agent execution. Redact only the disk copy, not live evidence.
      JSONArray diskHistory = new JSONArray(state.history.toString());
      for (int i = 0; i < diskHistory.length(); i++) {
        JSONObject item = diskHistory.optJSONObject(i);
        if (item == null || !(item.opt("output") instanceof JSONArray)) continue;
        item.put("output", textEvidence(item.getJSONArray("output")));
      }
      JSONObject json =
          new JSONObject()
              .put("id", state.id)
              .put("created", state.created)
              .put("last_used", state.lastUsed)
              .put("history", diskHistory)
              .put("transcript", state.transcript);
      if (!directory.isDirectory() && !directory.mkdirs())
        throw new IOException("Workspace unavailable");
      File temporary = new File(directory, "session.tmp");
      try (FileOutputStream stream = new FileOutputStream(temporary)) {
        stream.write(json.toString().getBytes("UTF-8"));
        stream.getFD().sync();
      }
      Files.move(
          temporary.toPath(),
          new File(directory, "session.json").toPath(),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    }
  }
}
