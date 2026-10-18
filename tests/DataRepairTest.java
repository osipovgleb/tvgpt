package local.tvgpt.assistant;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import local.tvgpt.assistant.data.*;
import org.json.*;

public final class DataRepairTest {
  static void require(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    try {
      run();
      System.exit(0);
    } catch (Throwable failure) {
      failure.printStackTrace();
      System.exit(1);
    }
  }

  private static void run() throws Exception {
    Context c = new Context();
    ReminderRepository r = new ReminderRepository(c);
    SQLiteDatabase db = AppDatabase.get(c).getWritableDatabase();
    long id =
        r.create(
            "claimed", System.currentTimeMillis() + 60000, "UTC", "REMINDER", "NONE", 0, 0, "", "");
    db.execSQL("UPDATE reminders SET due=1 WHERE id=?", new Object[] {id});
    require(r.claim(id), "claim due");
    require(!r.cancel(id), "cancel must not promise success after dispatch boundary");
    r.recoverInterrupted();
    require(
        r.list().getJSONObject(0).getString("status").equals("unconfirmed"),
        "abandoned dispatch must remain uncertain, never replayed");
    require(r.pending(System.currentTimeMillis()).length() == 0, "recovery must not replay action");
    for (int i = 0; i < 205; i++) {
      long next =
          r.create(
              "backlog " + i,
              System.currentTimeMillis() + 60000,
              "UTC",
              "REMINDER",
              "NONE",
              0,
              0,
              "",
              "");
      db.execSQL("UPDATE reminders SET due=1 WHERE id=?", new Object[] {next});
    }
    r.deferDue(System.currentTimeMillis());
    JSONArray batch = r.pending(System.currentTimeMillis());
    require(batch.length() == 200, "bounded dispatch batch");
    for (int i = 0; i < batch.length(); i++) {
      JSONObject item = batch.getJSONObject(i);
      require(r.claim(item.getLong("id")), "claim batch");
      r.finish(item, "notified");
    }
    require(r.nextDue(true) == 1, "remaining backlog must reschedule while interactive");
    require(r.nextDue(false) == Long.MAX_VALUE, "pending backlog must not spin in standby");
    require(
        r.pending(System.currentTimeMillis()).length() == 5, "five remaining after bounded batch");
    KnowledgeRepository k = new KnowledgeRepository(c);
    long note = 0;
    for (int i = 0; i < 100; i++) note = k.save(0, 0, "note " + i, "z".repeat(4000), "test");
    JSONObject page = k.page(0, 10);
    require(page.toString().length() < 5000, "knowledge metadata page exceeds budget");
    require(page.getJSONArray("items").length() == 10, "knowledge page size");
    require(!page.getJSONArray("items").getJSONObject(0).has("text"), "list leaks full text");
    require(page.getInt("next_offset") == 10, "knowledge continuation");
    JSONObject read = k.read(note, 0, 1000);
    require(
        read.getString("text").length() == 1000 && read.getInt("next_offset") == 1000,
        "bounded note read");
    require(k.read(note, 4000, 1000).getInt("next_offset") == -1, "end of note");
    db.execSQL("DELETE FROM reminders");
    long one =
        r.create(
            "a".repeat(1000),
            System.currentTimeMillis() + 60000,
            "UTC",
            "REMINDER",
            "NONE",
            0,
            0,
            "",
            "");
    long two =
        r.create(
            "b".repeat(1000),
            System.currentTimeMillis() + 60000,
            "UTC",
            "REMINDER",
            "NONE",
            0,
            0,
            "",
            "");
    long three =
        r.create(
            "c".repeat(1000),
            System.currentTimeMillis() + 60000,
            "UTC",
            "REMINDER",
            "NONE",
            0,
            0,
            "",
            "");
    db.execSQL("UPDATE reminders SET due=1");
    local.tvgpt.assistant.reminders.ReminderDispatcher.onDue(c);
    require(
        r.list().getJSONObject(0).getString("status").equals("delivering"),
        "must wait for delivery callback");
    for (int i = 0; i < 3; i++) {
      java.util.function.Consumer<String> done =
          local.tvgpt.assistant.reminders.ReminderSpeech.pending;
      local.tvgpt.assistant.reminders.ReminderSpeech.pending = null;
      done.accept(i == 0 ? "completed" : "failed");
      java.util.concurrent.CountDownLatch barrier = new java.util.concurrent.CountDownLatch(1);
      local.tvgpt.assistant.reminders.ReminderDispatcher.submit(barrier::countDown);
      require(barrier.await(5, java.util.concurrent.TimeUnit.SECONDS), "delivery worker timeout");
    }
    require(
        android.app.NotificationManager.bodies.size() == 3,
        "all bodies must receive notifications beyond 1500 aggregate");
    require(
        local.tvgpt.assistant.reminders.ReminderSpeech.bodies.size() == 3,
        "all bodies must reach speech");
    require(
        r.list().getJSONObject(0).getString("status").equals("completed"),
        "TTS completion outcome");
    require(
        r.list().getJSONObject(1).getString("status").equals("notified"),
        "notification-only must not claim speech");
    long fail =
        r.create(
            "denied", System.currentTimeMillis() + 60000, "UTC", "REMINDER", "NONE", 0, 0, "", "");
    db.execSQL("UPDATE reminders SET due=1 WHERE id=?", new Object[] {fail});
    android.app.NotificationManager.enabled = false;
    android.os.PowerManager.interactive = false;
    local.tvgpt.assistant.reminders.ReminderDispatcher.onDue(c);
    require(
        r.pending(System.currentTimeMillis()).length() == 1, "standby must retain pending work");
    require(
        local.tvgpt.assistant.reminders.ReminderSpeech.bodies.size() == 3,
        "standby must not speak");
    android.os.PowerManager.interactive = true;
    local.tvgpt.assistant.reminders.ReminderDispatcher.onDue(c);
    local.tvgpt.assistant.reminders.ReminderSpeech.pending.accept("failed");
    java.util.concurrent.CountDownLatch barrier = new java.util.concurrent.CountDownLatch(1);
    local.tvgpt.assistant.reminders.ReminderDispatcher.submit(barrier::countDown);
    require(barrier.await(5, java.util.concurrent.TimeUnit.SECONDS), "failure completion timeout");
    JSONObject failed = null;
    for (int i = 0; i < r.list().length(); i++)
      if (r.list().getJSONObject(i).getLong("id") == fail) failed = r.list().getJSONObject(i);
    require(
        failed != null && failed.getString("status").equals("failed"),
        "denied notifications plus TTS failure must fail");
    db.execSQL("DELETE FROM reminders");
    android.app.NotificationManager.enabled = true;
    long first =
        r.create(
            "first", System.currentTimeMillis() + 60000, "UTC", "REMINDER", "NONE", 0, 0, "", "");
    long edited =
        r.create(
            "old body",
            System.currentTimeMillis() + 60000,
            "UTC",
            "REMINDER",
            "NONE",
            0,
            0,
            "",
            "");
    db.execSQL("UPDATE reminders SET due=1");
    int before = local.tvgpt.assistant.reminders.ReminderSpeech.bodies.size();
    local.tvgpt.assistant.reminders.ReminderDispatcher.onDue(c);
    r.edit(edited, 1, "new body", System.currentTimeMillis() + 60000, "UTC", "NONE", 0);
    // The edited deadline passes while an earlier item is still speaking.
    db.execSQL("UPDATE reminders SET due=1 WHERE id=?", new Object[] {edited});
    local.tvgpt.assistant.reminders.ReminderSpeech.pending.accept("completed");
    java.util.concurrent.CountDownLatch editedBarrier = new java.util.concurrent.CountDownLatch(1);
    local.tvgpt.assistant.reminders.ReminderDispatcher.submit(editedBarrier::countDown);
    require(editedBarrier.await(5, java.util.concurrent.TimeUnit.SECONDS), "edited batch timeout");
    require(
        local.tvgpt.assistant.reminders.ReminderSpeech.bodies.size() == before + 1,
        "stale batch must not claim and deliver an edited reminder");
    local.tvgpt.assistant.reminders.ReminderDispatcher.onDue(c);
    require(
        local.tvgpt.assistant.reminders.ReminderSpeech.bodies.get(before + 1).equals("new body"),
        "fresh batch must deliver edited content");
    local.tvgpt.assistant.reminders.ReminderSpeech.pending.accept("completed");
    System.out.println(
        "Data repair cancellation/recovery/backlog/knowledge/dispatcher/standby/failure/revision"
            + " passed");
  }
}
