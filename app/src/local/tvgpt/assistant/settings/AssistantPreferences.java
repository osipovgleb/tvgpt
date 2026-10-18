package local.tvgpt.assistant.settings;

import android.content.Context;
import android.content.SharedPreferences;

public final class AssistantPreferences {
  private final SharedPreferences prefs;

  public AssistantPreferences(Context c) {
    prefs = c.getSharedPreferences("appearance_audio", 0);
  }

  public String theme() {
    return OrbPalette.valid(read("theme", "sky"));
  }

  public void theme(String id) {
    prefs.edit().putString("theme", OrbPalette.valid(id)).apply();
  }

  public String voice() {
    String id = read("voice", "cove");
    if ("cove".equals(id)) return id;
    try {
      return id.matches("[a-z]{2,24}") && prefs.getBoolean("verified_" + id, false) ? id : "cove";
    } catch (ClassCastException e) {
      return "cove";
    }
  }

  public void verifiedVoice(String id) {
    if (id == null || !id.matches("[a-z]{2,24}")) throw new IllegalArgumentException("voice");
    prefs.edit().putString("voice", id).putBoolean("verified_" + id, true).apply();
  }

  public SpeechPreset preset() {
    return SpeechPreset.parse(read("preset", "NORMAL"));
  }

  public void preset(SpeechPreset p) {
    prefs.edit().putString("preset", p.name()).apply();
  }

  private String read(String key, String fallback) {
    try {
      return prefs.getString(key, fallback);
    } catch (ClassCastException e) {
      return fallback;
    }
  }
}
