package local.tvgpt.assistant.reminders;

import android.content.*;

public final class BootReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent i) {
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
