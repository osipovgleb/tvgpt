package local.tvgpt.assistant.digests;

import android.content.Context;
import java.util.concurrent.atomic.AtomicBoolean;
import local.tvgpt.assistant.SubscriptionImageGenerator;

public final class DigestWorker {
  private static volatile AtomicBoolean active;

  private DigestWorker() {}

  public static void cancel() {
    AtomicBoolean run = active;
    if (run != null) run.set(true);
  }

  public static void run(Context c, String jobId, AtomicBoolean stopped) {
    active = stopped;
    try {
      new DigestAssembler(c, new SubscriptionImageGenerator(c)).run(jobId, stopped);
    } finally {
      active = null;
    }
  }
}
