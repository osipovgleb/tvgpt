package local.tvgpt.assistant;

import android.app.*;
import android.os.Bundle;
import local.tvgpt.assistant.data.KnowledgeRepository;
import org.json.*;

public final class DataCheck extends Instrumentation {
  private String mode;

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    mode = args == null ? "features" : args.getString("mode", "features");
    start();
  }

  @Override
  public void onStart() {
    Bundle out = new Bundle();
    try {
      if (mode.equals("preferences")) {
        getTargetContext()
            .getSharedPreferences("appearance_audio", 0)
            .edit()
            .putInt("theme", 7)
            .putString("voice", "unverified")
            .putInt("preset", 9)
            .commit();
        local.tvgpt.assistant.settings.AssistantPreferences prefs =
            new local.tvgpt.assistant.settings.AssistantPreferences(getTargetContext());
        FeatureChecks.require(
            prefs.theme().equals("sky")
                && prefs.voice().equals("cove")
                && prefs.preset() == local.tvgpt.assistant.settings.SpeechPreset.NORMAL,
            "invalid preference fallback");
        for (String id : local.tvgpt.assistant.settings.OrbPalette.IDS) {
          prefs.theme(id);
          FeatureChecks.require(prefs.theme().equals(id), "theme retention");
        }
        getTargetContext().getSharedPreferences("appearance_audio", 0).edit().clear().commit();
        out.putBoolean("corrupt_defaults_and_12_themes", true);
      } else if (mode.equals("write")) {
        long id =
            new KnowledgeRepository(getTargetContext())
                .save(0, 0, "Проверка перезапуска", "Сохранено на диск", "test");
        getTargetContext().getSharedPreferences("test", 0).edit().putLong("note", id).commit();
        out.putBoolean("persisted", true);
      } else if (mode.equals("verify")) {
        long id = getTargetContext().getSharedPreferences("test", 0).getLong("note", -1);
        JSONArray notes = new KnowledgeRepository(getTargetContext()).list();
        boolean found = false;
        for (int i = 0; i < notes.length(); i++)
          if (notes.getJSONObject(i).optLong("id") == id)
            found = notes.getJSONObject(i).optString("text").equals("Сохранено на диск");
        FeatureChecks.require(found, "lost after process restart");
        FeatureChecks.require(new KnowledgeRepository(getTargetContext()).delete(id, 1), "cleanup");
        out.putBoolean("survived_process_restart", true);
      } else FeatureChecks.database(getTargetContext(), out);
      finish(Activity.RESULT_OK, out);
    } catch (Throwable e) {
      out.putString("error", e.toString());
      finish(Activity.RESULT_CANCELED, out);
    }
  }
}
