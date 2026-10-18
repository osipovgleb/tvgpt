package local.tvgpt.assistant;

import android.content.*;

/**
 * Package-scoped invalidation only; receivers read current data, never trust broadcast payloads.
 */
public final class AppDataEvents {
  public static final String ACTION = "local.tvgpt.assistant.DATA_CHANGED";
  public static final String KNOWLEDGE = "knowledge", REMINDERS = "reminders", DIGESTS = "digests";

  private AppDataEvents() {}

  public static void changed(Context context, String section) {
    try {
      context.sendBroadcast(
          new Intent(ACTION).setPackage(context.getPackageName()).putExtra("section", section));
    } catch (RuntimeException unavailable) {
      android.util.Log.w(
          "TVGPTData", "UI invalidation unavailable: " + unavailable.getClass().getSimpleName());
    }
  }
}
