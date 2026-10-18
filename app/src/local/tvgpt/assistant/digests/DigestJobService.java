package local.tvgpt.assistant.digests;

import android.app.job.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import local.tvgpt.assistant.data.DigestRepository;
import org.json.*;

public final class DigestJobService extends JobService {
  private static final ExecutorService worker = Executors.newSingleThreadExecutor();
  private AtomicBoolean systemStopped = new AtomicBoolean();
  private AtomicBoolean stopped = new AtomicBoolean();

  public boolean onStartJob(JobParameters params) {
    DigestScheduler.HANDOFF.started(params);
    final AtomicBoolean run = new AtomicBoolean();
    stopped = run;
    final AtomicBoolean systemRun = new AtomicBoolean();
    systemStopped = systemRun;
    worker.submit(
        () -> {
          try {
            DigestRepository repo = new DigestRepository(this);
            for (int i = 0; i < 20 && !run.get(); i++) {
              JSONObject job =
                  repo.claim(System.currentTimeMillis(), System.currentTimeMillis() + 900000);
              if (job == null) break;
              DigestWorker.run(this, job.getString("job_id"), run);
            }
          } catch (Exception e) {
            android.util.Log.w("TVGPTDigest", "Worker: " + e.getClass().getSimpleName());
          } finally {
            if (!systemRun.get())
              DigestScheduler.HANDOFF.completed(
                  params,
                  () -> jobFinished(params, false),
                  () -> new DigestRepository(this).hasPending(),
                  () -> DigestScheduler.schedule(this));
          }
        });
    return true;
  }

  public boolean onStopJob(JobParameters params) {
    DigestScheduler.HANDOFF.stopped(params);
    systemStopped.set(true);
    stopped.set(true);
    return true;
  }

  public void onDestroy() {
    systemStopped.set(true);
    stopped.set(true);
    super.onDestroy();
  }
}
