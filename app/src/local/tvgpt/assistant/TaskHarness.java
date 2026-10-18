package local.tvgpt.assistant;

import java.util.*;
import org.json.*;

/** Per-call task identities and receipts; cancellation never replays a mutation. */
final class TaskHarness {
  static final class Task {
    final String id, inputId, text;
    final JSONArray context;
    final CancellationToken token = new CancellationToken();
    private final Map<String, Object> receipts = new HashMap<>();
    volatile String status = "running";
    private final Set<String> claimed = new HashSet<>();
    private final LinkedHashSet<String> recipients = new LinkedHashSet<>();
    volatile String result;
    private final JSONArray toolEvents = new JSONArray();

    synchronized void tool(String name, Object value) {
      try {
        JSONObject row = new JSONObject().put("tool", name).put("at", System.currentTimeMillis());
        if (value instanceof String && ((String) value).startsWith("{")) {
          JSONObject parsed = new JSONObject((String) value);
          for (String key :
              new String[] {"status", "confirmed_scope", "evidence_scope", "screen_owner_task_id"})
            if (parsed.has(key)) row.put(key, parsed.opt(key));
        }
        toolEvents.put(row);
        while (toolEvents.length() > 12) toolEvents.remove(0);
      } catch (Exception ignored) {
      }
    }

    synchronized JSONArray toolEvents() throws Exception {
      return new JSONArray(toolEvents.toString());
    }

    synchronized void alias(String id) {
      recipients.add(id);
    }

    synchronized List<String> complete(String result) {
      if (token.cancelled() || !status.equals("running")) return Collections.emptyList();
      this.result = result;
      status = "completed";
      return claimPending();
    }

    synchronized List<String> claimPending() {
      List<String> pending = new ArrayList<>();
      if (result != null) for (String id : recipients) if (claimed.add(id)) pending.add(id);
      return pending;
    }

    synchronized String claim(String id) {
      return result != null && claimed.add(id) ? result : null;
    }

    synchronized boolean cancel() {
      if (!status.equals("running") && !status.equals("completed")) return false;
      status = "cancelled";
      result = "{\"status\":\"cancelled\",\"speech\":\"Поручение отменено.\"}";
      token.cancel();
      return true;
    }

    Task(String id, String inputId, String text, JSONArray context) {
      recipients.add(id);
      this.id = id;
      this.inputId = inputId;
      this.text = text;
      this.context = context;
    }

    synchronized Object receipt(String id) {
      return receipts.get(id);
    }

    synchronized void receipt(String id, Object result) {
      receipts.put(id, result);
    }
  }

  private final LinkedHashMap<String, Task> tasks = new LinkedHashMap<>();
  final java.util.concurrent.locks.ReentrantLock actions =
      new java.util.concurrent.locks.ReentrantLock();
  private boolean closed;
  private Task screenOwner;

  synchronized boolean claimScreen(Task task) {
    task.token.check();
    if (closed || (screenOwner != null && screenOwner != task)) return false;
    screenOwner = task;
    return true;
  }

  synchronized void releaseScreen(Task task) {
    if (screenOwner == task) screenOwner = null;
  }

  synchronized String screenOwner() {
    return screenOwner == null ? "" : screenOwner.id;
  }

  static boolean usesScreen(String name) {
    return name.equals("open_app")
        || name.equals("search_app")
        || name.equals("read_screen")
        || name.equals("screenshot")
        || name.equals("wait_for_screen")
        || name.equals("tap")
        || name.equals("click")
        || name.equals("type_text")
        || name.equals("navigate")
        || name.equals("navigate_sequence");
  }

  synchronized Task admit(String id, String inputId, String text, JSONArray context) {
    if (closed || tasks.containsKey(id)) return null;
    for (Task existing : tasks.values()) {
      if (!inputId.isEmpty() && existing.inputId.equals(inputId) && existing.text.equals(text)) {
        existing.alias(id);
        tasks.put(id, existing);
        return null;
      }
    }
    if (tasks.size() >= 128) {
      Iterator<Map.Entry<String, Task>> entries = tasks.entrySet().iterator();
      while (entries.hasNext() && tasks.size() >= 96)
        if (!entries.next().getValue().status.equals("running")) entries.remove();
    }
    Task task = new Task(id, inputId, text, context);
    tasks.put(id, task);
    return task;
  }

  synchronized JSONArray status() throws Exception {
    JSONArray result = new JSONArray();
    List<Task> recent = new ArrayList<>(new LinkedHashSet<>(tasks.values()));
    for (int i = 0; i < recent.size(); i++) {
      Task t = recent.get(i);
      if (!t.status.equals("running") && i < recent.size() - 4) continue;
      JSONObject item =
          new JSONObject()
              .put("task_id", t.id)
              .put("input_turn_id", t.inputId)
              .put("request", t.text.substring(0, Math.min(1000, t.text.length())))
              .put("status", t.status)
              .put("tools", t.toolEvents());
      if (t.result != null)
        item.put("result", t.result.substring(0, Math.min(2000, t.result.length())));
      result.put(item);
    }
    return result;
  }

  synchronized String inputId(String id) {
    Task t = tasks.get(id);
    return t == null ? "" : t.inputId;
  }

  synchronized CancellationToken token(String id) {
    Task t = tasks.get(id);
    return t == null ? CancellationToken.NONE : t.token;
  }

  synchronized boolean sameTask(Task task, String id) {
    return task != null && tasks.get(id) == task;
  }

  boolean cancel(String id) {
    Task t;
    synchronized (this) {
      t = tasks.get(id);
    }
    return t != null && t.cancel();
  }

  synchronized String result(String id) {
    Task t = tasks.get(id);
    return t == null ? null : t.claim(id);
  }

  void close() {
    List<Task> pending;
    synchronized (this) {
      closed = true;
      pending = new ArrayList<>(tasks.values());
    }
    for (Task t : pending) {
      t.cancel();
      t.token.cancel();
    }
  }
}
