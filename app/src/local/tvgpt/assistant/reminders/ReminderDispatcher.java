package local.tvgpt.assistant.reminders;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;
import local.tvgpt.assistant.data.ReminderRepository;
import org.json.*;

public final class ReminderDispatcher {
  private static final ExecutorService IO =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "tvgpt-reminders"));

  private static final java.util.concurrent.atomic.AtomicBoolean recovered =
      new java.util.concurrent.atomic.AtomicBoolean();
  // Accessed only by IO, including every asynchronous completion.
  private static boolean delivering;

  private ReminderDispatcher() {}

  public static void onProcessStart(Context c) {
    Context app = c.getApplicationContext();
    submit(() -> onDue(app));
  }

  private static void recover(Context c) {
    if (!recovered.get()) {
      new ReminderRepository(c).recoverInterrupted();
      recovered.set(true);
    }
  }

  public static void submit(Runnable action) {
    IO.submit(action);
  }

  public static boolean interactive(Context c) {
    return c.getSystemService(PowerManager.class).isInteractive();
  }

  public static void onInteractive(Context c) {
    Context app = c.getApplicationContext();
    submit(() -> onDue(app));
  }

  public static void onDue(Context c) {
    recover(c);
    if (delivering) return;
    ReminderRepository repo = new ReminderRepository(c);
    long now = System.currentTimeMillis();
    AlarmScheduler.reconcile(c);
    repo.deferDue(now);
    AlarmScheduler.reconcile(c);
    if (!interactive(c)) return;
    JSONArray due = repo.pending(now);
    delivering = true;
    deliverNext(c, repo, due, 0);
  }

  private static void deliverNext(Context c, ReminderRepository repo, JSONArray due, int index) {
    while (index < due.length() && interactive(c)) {
      JSONObject item = due.optJSONObject(index++);
      if (!repo.claim(item.optLong("id"), item.optLong("revision"))) continue;
      String action = null;
      try {
        if (item.optString("kind").equals("ACTION")) {
          if (!interactive(c)) throw new IllegalStateException("screen off");
          action = local.tvgpt.assistant.ReminderActions.execute(c, item.optString("action"));
        }
      } catch (Exception e) {
        // The action adapter preserves unconfirmed transport dispatch outcomes.
        action = "unconfirmed";
      }
      String actionOutcome = action;
      int nextIndex = index;
      // Deliver every body independently. There is no aggregate truncation or QUEUE_FLUSH race.
      String message = item.optString("body");
      boolean notified = notify(c, item.optLong("id"), message);
      ReminderSpeech.speak(
          c,
          message,
          spoken ->
              submit(
                  () -> {
                    try {
                      repo.finish(item, DeliveryOutcome.combine(actionOutcome, notified, spoken));
                      deliverNext(c, repo, due, nextIndex);
                    } catch (RuntimeException e) {
                      delivering = false;
                      android.util.Log.w("TVGPTReminders", "Delivery completion unavailable", e);
                      AlarmScheduler.reconcile(c);
                    }
                  }));
      return;
    }
    delivering = false;
    // A bounded batch may leave due rows. nextDue includes pending while interactive.
    AlarmScheduler.reconcile(c);
  }

  private static boolean notify(Context c, long id, String message) {
    if (!interactive(c)) return false;
    try {
      NotificationManager notifications = c.getSystemService(NotificationManager.class);
      notifications.createNotificationChannel(
          new NotificationChannel(
              "reminders", "Напоминания", NotificationManager.IMPORTANCE_DEFAULT));
      NotificationChannel channel = notifications.getNotificationChannel("reminders");
      if (!notifications.areNotificationsEnabled()
          || (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE))
        return false;
      notifications.notify(
          "reminder-" + id,
          51,
          new Notification.Builder(c, "reminders")
              .setSmallIcon(android.R.drawable.ic_dialog_info)
              .setContentTitle("TV GPT · Напоминание")
              .setContentText(message)
              .setStyle(new Notification.BigTextStyle().bigText(message))
              .setAutoCancel(true)
              .build());
      local.tvgpt.assistant.ReminderPopup.show(message);
      return true;
    } catch (RuntimeException e) {
      android.util.Log.w("TVGPTReminders", "Notification unavailable", e);
      return false;
    }
  }
}
