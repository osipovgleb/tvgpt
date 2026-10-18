package local.tvgpt.assistant.reminders;

import android.content.*;
import local.tvgpt.assistant.data.ReminderRepository;

public final class BootReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent i) {
    PendingResult pending = goAsync();
    ReminderDispatcher.submit(
        () -> {
          try {
            if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()))
              new ReminderRepository(c).recoverInterrupted();
            ReminderDispatcher.onDue(c.getApplicationContext());
          } finally {
            pending.finish();
          }
        });
  }
}
