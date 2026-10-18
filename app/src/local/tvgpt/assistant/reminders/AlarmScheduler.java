package local.tvgpt.assistant.reminders;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import local.tvgpt.assistant.data.ReminderRepository;
import org.json.*;

public final class AlarmScheduler {
  private AlarmScheduler() {}

  public static String boot(Context c) {
    return "" + Settings.Global.getInt(c.getContentResolver(), "boot_count", 0);
  }

  public static boolean exact(Context c) {
    AlarmManager a = c.getSystemService(AlarmManager.class);
    return Build.VERSION.SDK_INT < 31 || a.canScheduleExactAlarms();
  }

  public static void reconcile(Context c) {
    ReminderRepository repo = new ReminderRepository(c);
    JSONArray items = repo.list();
    AlarmManager alarms = c.getSystemService(AlarmManager.class);
    long now = System.currentTimeMillis();
    long next = Long.MAX_VALUE;
    for (int i = 0; i < items.length(); i++) {
      JSONObject item = items.optJSONObject(i);
      if (item.optInt("enabled") != 1 || !item.optString("status").equals("scheduled")) continue;
      long due = item.optLong("due");
      if (item.optString("kind").equals("TIMER") && item.optString("boot").equals(boot(c))) {
        due =
            SchedulePolicy.timerWallDue(
                now, SystemClock.elapsedRealtime(), item.optLong("elapsed_due"), true, due);
        repo.timerDue(item.optLong("id"), due);
      }
      next = Math.min(next, due);
    }
    PendingIntent intent =
        PendingIntent.getBroadcast(
            c,
            41,
            new Intent(c, ReminderReceiver.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    alarms.cancel(intent);
    if (next != Long.MAX_VALUE) {
      if (exact(c)) {
        try {
          alarms.setExact(AlarmManager.RTC, Math.max(now + 100, next), intent);
        } catch (SecurityException e) {
          alarms.set(AlarmManager.RTC, Math.max(now + 100, next), intent);
        }
      } else alarms.set(AlarmManager.RTC, Math.max(now + 100, next), intent);
    }
  }
}
