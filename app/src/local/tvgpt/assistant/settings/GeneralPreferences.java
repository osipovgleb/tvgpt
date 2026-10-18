package local.tvgpt.assistant.settings;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.ZoneId;
import local.tvgpt.assistant.reminders.RemoteDateTime;

/** A persisted app timezone; changing it does not reinterpret existing reminders. */
public final class GeneralPreferences {
  private final SharedPreferences prefs;

  public GeneralPreferences(Context context) {
    prefs = context.getSharedPreferences("general", 0);
  }

  public ZoneId zone() {
    try {
      return RemoteDateTime.zone(prefs.getString("timezone", ""), ZoneId.systemDefault());
    } catch (ClassCastException e) {
      return ZoneId.systemDefault();
    }
  }

  public void zone(String id) {
    prefs.edit().putString("timezone", ZoneId.of(id).getId()).apply();
  }
}
