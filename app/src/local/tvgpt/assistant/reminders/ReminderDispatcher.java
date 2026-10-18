package local.tvgpt.assistant.reminders;

import android.app.*;
import android.content.*;
import android.os.*;
import android.speech.tts.*;
import java.util.*;
import java.util.concurrent.*;
import local.tvgpt.assistant.data.ReminderRepository;
import org.json.*;

public final class ReminderDispatcher {
  private static final ExecutorService IO =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "tvgpt-reminders"));

  private ReminderDispatcher() {}

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
    // Rebase duration timers before comparing calendar deadlines after TIME_SET.
    AlarmScheduler.reconcile(c);
    ReminderRepository repo = new ReminderRepository(c);
    long now = System.currentTimeMillis();
    repo.deferDue(now);
    if (!interactive(c)) {
      AlarmScheduler.reconcile(c);
      return;
    }
    JSONArray due = repo.pending(now);
    StringBuilder message = new StringBuilder();
    for (int i = 0; i < due.length(); i++) {
      if (!interactive(c)) break;
      JSONObject item = due.optJSONObject(i);
      if (!repo.claim(item.optLong("id"))) continue;
      String outcome = "completed";
      try {
        if (item.optString("kind").equals("ACTION")) {
          if (!interactive(c)) throw new IllegalStateException("screen off");
          outcome = local.tvgpt.assistant.ReminderActions.execute(c, item.optString("action"));
        }
        if (message.length() < 1500) message.append(item.optString("body")).append(". ");
      } catch (Exception e) {
        outcome = "failed";
      }
      repo.finish(item, outcome);
    }
    if (message.length() > 0 && interactive(c)) notifyAndSpeak(c, message.toString());
    AlarmScheduler.reconcile(c);
  }

  private static void notifyAndSpeak(Context c, String message) {
    NotificationManager notifications = c.getSystemService(NotificationManager.class);
    notifications.createNotificationChannel(
        new NotificationChannel(
            "reminders", "Напоминания", NotificationManager.IMPORTANCE_DEFAULT));
    try {
      notifications.notify(
          51,
          new Notification.Builder(c, "reminders")
              .setSmallIcon(android.R.drawable.ic_dialog_info)
              .setContentTitle("TV GPT · Напоминание")
              .setContentText(message)
              .setStyle(new Notification.BigTextStyle().bigText(message))
              .setAutoCancel(true)
              .build());
    } catch (SecurityException e) {
      android.util.Log.w("TVGPTReminders", "Notification access unavailable");
    }
    local.tvgpt.assistant.ReminderPopup.show(message);
    ReminderSpeech.speak(c, message);
  }
}
