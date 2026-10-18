package local.tvgpt.assistant;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.time.*;
import local.tvgpt.assistant.data.*;
import org.json.*;

public final class ReminderToolMutationTest {
  static final Context context = new Context();
  static final ReminderRepository repo = new ReminderRepository(context);
  static final long future = System.currentTimeMillis() + 3600000;

  public static void main(String[] args) throws Exception {
    try {
      schemasAndUnknown();
      calendarAndDelete();
      timer();
      cancellation();
      System.out.println(
          "Reminder tools: exposed CRUD, exact readback, stale revisions, claimed rows, timer"
              + " semantics, events and rollback passed");
    } finally {
      AppDatabase.get(context).getWritableDatabase().close();
    }
  }

  private static void schemasAndUnknown() throws Exception {
    JSONArray tools = new JSONArray();
    ReminderTools.schemas(tools);
    for (String name : new String[] {"reminder_update", "timer_update", "reminder_delete"}) {
      boolean found = false;
      for (int i = 0; i < tools.length(); i++)
        if (tools.getJSONObject(i).getString("name").equals(name)) found = true;
      require(found, "Missing tool " + name);
    }
    try {
      ReminderTools.execute(
          context, "reminder_typo", new JSONObject().put("body", "must not create"));
      throw new AssertionError("Unsupported tool fell through to create");
    } catch (IllegalArgumentException expected) {
    }
    require(repo.list().length() == 0, "Unknown tool mutated database");
  }

  private static JSONObject update(long id, long revision) throws Exception {
    return new JSONObject()
        .put("id", id)
        .put("revision", revision)
        .put("body", "updated")
        .put(
            "local_datetime",
            Instant.ofEpochMilli(future).atZone(ZoneOffset.UTC).toLocalDateTime().toString())
        .put("zone", "UTC")
        .put("repeat", "DAILY")
        .put("weekdays", 0);
  }

  private static void calendarAndDelete() throws Exception {
    long id = repo.create("original", future, "UTC", "ACTION", "NONE", 0, 0, "", "allowed-action");
    int notifications = AppDataEvents.notifications;
    JSONObject changed =
        (JSONObject) ReminderTools.execute(context, "reminder_update", update(id, 1));
    require(changed.getString("status").equals("updated"), "No confirmed edit");
    JSONObject current = repo.get(id);
    require(
        current.getLong("revision") == 2
            && current.getString("body").equals("updated")
            && current.getString("kind").equals("ACTION")
            && current.getString("action").equals("allowed-action")
            && current.getString("repeat").equals("DAILY"),
        "Edit changed identity/action or lost fields");
    require(AppDataEvents.notifications == notifications + 1, "Edit did not invalidate open list");
    JSONObject stale =
        (JSONObject) ReminderTools.execute(context, "reminder_update", update(id, 1));
    require(
        stale.getString("status").equals("conflict")
            && AppDataEvents.notifications == notifications + 1,
        "Conflict notified or changed data");
    JSONObject removed =
        (JSONObject)
            ReminderTools.execute(
                context, "reminder_delete", new JSONObject().put("id", id).put("revision", 2));
    require(
        removed.getString("status").equals("deleted") && repo.get(id) == null,
        "Delete left a record");
    long active = repo.create("claimed", future, "UTC", "REMINDER", "NONE", 0, 0, "", "");
    SQLiteDatabase db = AppDatabase.get(context).getWritableDatabase();
    db.execSQL("UPDATE reminders SET status='delivering' WHERE id=?", new Object[] {active});
    JSONObject blocked =
        (JSONObject)
            ReminderTools.execute(
                context, "reminder_delete", new JSONObject().put("id", active).put("revision", 1));
    require(
        blocked.getString("status").equals("conflict") && repo.get(active) != null,
        "Deleted claimed reminder");
  }

  private static void timer() throws Exception {
    long id = repo.create("timer", future, "UTC", "TIMER", "NONE", 0, 123456, "old-boot", "");
    JSONObject request =
        new JSONObject().put("id", id).put("revision", 1).put("body", "renamed").put("seconds", 0);
    ReminderTools.execute(context, "timer_update", request);
    JSONObject renamed = repo.get(id);
    require(
        renamed.getLong("due") == future
            && renamed.getLong("elapsed_due") == 123456
            && renamed.getString("boot").equals("old-boot"),
        "Rename reset timer");
    ReminderTools.execute(context, "timer_update", request.put("revision", 2).put("seconds", 90));
    JSONObject reset = repo.get(id);
    require(
        reset.getString("kind").equals("TIMER")
            && reset.getLong("elapsed_due") == 190000
            && reset.getString("boot").equals("test-boot"),
        "Timer became calendar reminder");
    try {
      ReminderTools.execute(context, "reminder_update", update(id, 3));
      throw new AssertionError("Wrong edit converted timer");
    } catch (IllegalArgumentException expected) {
    }
    require(repo.get(id).getLong("revision") == 3, "Rejected timer edit changed revision");
  }

  private static void cancellation() throws Exception {
    long id = repo.create("retained", future, "UTC", "REMINDER", "NONE", 0, 0, "", "");
    CancellationToken token = new CancellationToken();
    SQLiteDatabase.afterSql =
        sql -> {
          if (sql.startsWith("DELETE FROM reminders")) token.cancel();
        };
    int notifications = AppDataEvents.notifications;
    try {
      ReminderTools.execute(
          context, "reminder_delete", new JSONObject().put("id", id).put("revision", 1), token);
      throw new AssertionError("Cancelled delete committed");
    } catch (java.util.concurrent.CancellationException expected) {
    } finally {
      SQLiteDatabase.afterSql = null;
    }
    require(
        repo.get(id) != null && AppDataEvents.notifications == notifications,
        "Cancelled deletion notified or persisted");
  }

  static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
