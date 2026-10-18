package local.tvgpt.assistant.digests;

import android.content.*;

public final class DigestReceiver extends BroadcastReceiver {
  public void onReceive(Context c, Intent intent) {
    PendingResult pending = goAsync();
    new Thread(
            () -> {
              try {
                DigestScheduler.reconcile(c);
              } finally {
                pending.finish();
              }
            },
            "tvgpt-digest-reconcile")
        .start();
  }
}
