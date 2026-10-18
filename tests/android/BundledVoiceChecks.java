package local.tvgpt.assistant;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import local.tvgpt.assistant.settings.SpeechPreset;
import local.tvgpt.assistant.settings.VoiceCatalogue;

/** Exercises the APK assets and real Android decoder, without a Live request. */
final class BundledVoiceChecks {
  static void run(Instrumentation test, Bundle result) throws Exception {
    Context context = test.getTargetContext();
    for (String voice : VoiceCatalogue.CANDIDATES) {
      long duration;
      MediaMetadataRetriever metadata = new MediaMetadataRetriever();
      try (AssetFileDescriptor asset =
          context.getAssets().openFd("voice-samples/" + voice + ".m4a")) {
        metadata.setDataSource(
            asset.getFileDescriptor(), asset.getStartOffset(), asset.getLength());
        duration =
            Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        if (duration < 500) throw new Exception("Empty preview: " + voice);
      } finally {
        metadata.release();
      }
      CountDownLatch completed = new CountDownLatch(1);
      AtomicReference<String> failure = new AtomicReference<>();
      VoiceSamples samples = new VoiceSamples(context);
      try {
        test.runOnMainSync(
            () ->
                samples.play(
                    voice,
                    SpeechPreset.NORMAL,
                    new VoiceSamples.Listener() {
                      public void status(String text) {
                        if (!text.isEmpty()) {
                          failure.set(text);
                          completed.countDown();
                        }
                      }

                      public void ready(String id) {
                        if (!voice.equals(id)) failure.set("Wrong voice: " + id);
                        completed.countDown();
                      }

                      public void unavailable(String id) {
                        failure.set("Unavailable preview: " + id);
                        completed.countDown();
                      }
                    }));
        if (!completed.await(duration + 5000, TimeUnit.MILLISECONDS))
          throw new Exception("Preview did not complete: " + voice);
        if (failure.get() != null) throw new Exception(voice + ": " + failure.get());
        result.putLong(voice + "_played_ms", duration);
        android.util.Log.i(
            "TVGPTPreview", "Device sample completed voice=" + voice + " duration=" + duration);
      } finally {
        test.runOnMainSync(samples::stop);
      }
    }
    result.putBoolean("bundled_voices_completed", true);
  }
}
