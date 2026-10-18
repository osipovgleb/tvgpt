package local.tvgpt.assistant.reminders;

import android.content.*;

public final class ReminderReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent intent) {
    PendingResult pending = goAsync();
    ReminderDispatcher.submit(
        () -> {
          try {
            ReminderDispatcher.onDue(c.getApplicationContext());
          } finally {
            pending.finish();
          }
        });
  }
}
