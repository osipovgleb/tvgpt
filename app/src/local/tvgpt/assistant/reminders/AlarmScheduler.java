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
    AlarmManager alarms = c.getSystemService(AlarmManager.class);
    long now = System.currentTimeMillis();
    repo.rebaseTimers(now, SystemClock.elapsedRealtime(), boot(c));
    long next = repo.nextDue(ReminderDispatcher.interactive(c));
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
