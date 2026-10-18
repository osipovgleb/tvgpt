package local.tvgpt.assistant.digests;

import android.app.*;
import android.app.job.*;
import android.content.*;
import local.tvgpt.assistant.data.DigestRepository;
import org.json.*;

/** Alarm only requests durable work. It does not wake the device or perform HTTP. */
public final class DigestScheduler {
  static final DigestJobHandoff HANDOFF = new DigestJobHandoff();
  private static final int JOB = 207, ALARM = 208;

  private DigestScheduler() {}

  public static void reconcile(Context context) {
    Context c = context.getApplicationContext();
    try {
      DigestRepository repo = new DigestRepository(c);
      long now = System.currentTimeMillis(), next = Long.MAX_VALUE;
      repo.recover(now);
      if (local.tvgpt.assistant.DigestResearch.authorized(c)) repo.resumeAuthorizedJobs();
      JSONArray schedules = repo.dueSchedules(now);
      SharedPreferences prefs = c.getSharedPreferences("digest_schedule", 0);
      for (int i = 0; i < schedules.length(); i++) {
        JSONObject s = schedules.getJSONObject(i);
        if (!s.optBoolean("enabled", true)) continue;
        JSONObject parent = repo.read("digest", s.getString("digest_id"));
        if (parent == null || !parent.optBoolean("enabled", true)) continue;
        String id = s.getString("id"), key = id + ":" + s.getLong("revision");
        long last = prefs.getLong(key, s.optLong("created", now)),
            slot = DigestSchedulePolicy.slot(s, now);
        if (slot > last) {
          repo.enqueue(s.getString("digest_id"), key + ":" + slot, id, slot, now);
          prefs.edit().putLong(key, slot).commit();
        }
        next = Math.min(next, DigestSchedulePolicy.next(s, now));
      }
      AlarmManager alarms = c.getSystemService(AlarmManager.class);
      PendingIntent intent =
          PendingIntent.getBroadcast(
              c,
              ALARM,
              new Intent(c, DigestReceiver.class),
              PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      alarms.cancel(intent);
      if (next != Long.MAX_VALUE) alarms.set(AlarmManager.RTC, next, intent);
      HANDOFF.request(repo::hasPending, () -> schedule(c));
    } catch (Exception e) {
      android.util.Log.w("TVGPTDigest", "Schedule reconciliation: " + e.getClass().getSimpleName());
    }
  }

  static void schedule(Context c) {
    c.getSystemService(JobScheduler.class)
        .schedule(
            new JobInfo.Builder(JOB, new ComponentName(c, DigestJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build());
  }
}
