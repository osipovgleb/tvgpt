package local.tvgpt.assistant;

import android.content.Context;
import android.os.Bundle;

/** Self-targeted Android preferences: independent migration and persistence, no account/network. */
public final class ModelSettingsChecks {
  public static void run(Context c, Bundle out) {
    android.content.SharedPreferences prefs = c.getSharedPreferences("models", 0);
    prefs.edit().clear().commit();
    ModelSettings.set(c, "discussion", "gpt-6-astra");
    FeatureChecks.require(
        ModelSettings.digests(c).equals("gpt-6.1-sol"),
        "discussion change moved unmigrated digest choice");
    ModelSettings.set(c, "digests", "gpt-6-sol");
    FeatureChecks.require(
        ModelSettings.discussion(c).equals("gpt-6-astra"), "digest choice moved discussion");
    FeatureChecks.require(
        ModelSettings.digests(c).equals("gpt-6-sol"), "digest choice not retained");
    prefs.edit().putInt("digests", 7).commit();
    FeatureChecks.require(
        ModelSettings.digests(c).equals("gpt-6.1-sol"), "corrupt digest choice not safe");
    prefs.edit().clear().commit();
    out.putBoolean("digest_model_independence_migration_persistence", true);
  }
}
