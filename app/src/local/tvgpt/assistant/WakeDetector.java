package local.tvgpt.assistant;

import android.content.Context;
import android.media.*;
import android.util.Log;
import java.io.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

/** Exactly one idle recorder. It is stopped and released before conversation capture. */
final class WakeDetector {
  private final Context context;
  private final Runnable wake;
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
  private final WakeRecovery recovery = new WakeRecovery();
  private final Object lock = new Object();
  private volatile boolean closed;
  private volatile AudioRecord capture;
  private Model model;

  WakeDetector(Context c, Runnable wake) {
    context = c;
    this.wake = wake;
  }

  synchronized void start() {
    long ticket = recovery.start();
    if (ticket >= 0) executor.execute(() -> listen(ticket));
  }

  void stop() {
    recovery.stop();
    releaseCapture();
  }

  private void releaseCapture() {
    synchronized (lock) {
      AudioRecord r = capture;
      capture = null;
      if (r != null) {
        try {
          r.stop();
        } catch (Exception ignored) {
        }
        r.release();
      }
    }
  }

  void releaseIdleModel() {
    stop();
    if (closed) return;
    executor.execute(
        () -> {
          if (capture == null && model != null) {
            model.close();
            model = null;
          }
        });
  }

  synchronized void close() {
    if (closed) return;
    closed = true;
    recovery.close();
    stop();
    executor.execute(
        () -> {
          if (model != null) {
            model.close();
            model = null;
          }
        });
    executor.shutdown();
  }

  private File unpack() throws Exception {
    File folder = new File(context.getFilesDir(), "wake-ru-0.22");
    if (new File(folder, "ready").exists()) return new File(folder, "vosk-model-small-ru-0.22");
    folder.mkdirs();
    String prefix = folder.getCanonicalPath() + File.separator;
    long total = 0;
    try (ZipInputStream zip = new ZipInputStream(context.getAssets().open("wake-model.zip"))) {
      ZipEntry entry;
      byte[] bytes = new byte[32768];
      while ((entry = zip.getNextEntry()) != null) {
        File target = new File(folder, entry.getName());
        if (!target.getCanonicalPath().startsWith(prefix))
          throw new IOException("Invalid model path");
        if (entry.isDirectory()) {
          target.mkdirs();
          continue;
        }
        target.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(target)) {
          int count;
          while ((count = zip.read(bytes)) != -1) {
            total += count;
            if (total > 200000000) throw new IOException("Model too large");
            out.write(bytes, 0, count);
          }
        }
      }
    }
    new File(folder, "ready").createNewFile();
    return new File(folder, "vosk-model-small-ru-0.22");
  }

  private void listen(long ticket) {
    if (!recovery.active(ticket)) return;
    try {
      if (model == null) {
        Log.i("TVGPTWake", "Loading local Russian wake model");
        model = new Model(unpack().getPath());
      }
      if (!recovery.active(ticket)) return;
      try (Recognizer recognizer =
          new Recognizer(model, 16000, "[\"привет телевизор\",\"окей телевизор\",\"[unk]\"]")) {
        AudioRecord record;
        synchronized (lock) {
          if (!recovery.active(ticket)) return;
          int minimum =
              AudioRecord.getMinBufferSize(
                  16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
          record =
              new AudioRecord(
                  MediaRecorder.AudioSource.MIC,
                  16000,
                  AudioFormat.CHANNEL_IN_MONO,
                  AudioFormat.ENCODING_PCM_16BIT,
                  Math.max(16000, minimum * 4));
          capture = record;
          if (record.getState() != AudioRecord.STATE_INITIALIZED)
            throw new IOException("Wake microphone initialization failed");
          record.startRecording();
          if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
            throw new IOException("Wake microphone did not start");
        }
        Log.i("TVGPTWake", "Listening locally: Привет, телевизор");
        byte[] bytes = new byte[3200];
        long samples = 0;
        while (recovery.active(ticket)) {
          int count;
          synchronized (lock) {
            if (capture != record) break;
            count = record.read(bytes, 0, bytes.length, AudioRecord.READ_NON_BLOCKING);
          }
          if (count < 0) throw new IOException("Wake recording interrupted");
          if (count == 0) {
            android.os.SystemClock.sleep(20);
            continue;
          }
          samples += count / 2;
          boolean finalResult = recognizer.acceptWaveForm(bytes, count);
          // Partial hypotheses can briefly resemble the wake phrase during unrelated speech.
          // Never consume the wake ticket until the recognizer has finalized the utterance.
          if (!finalResult) continue;
          JSONObject result = new JSONObject(recognizer.getResult());
          if (WakePhrase.matches(result.optString("text"))) {
            Log.i("TVGPTWake", "Final wake phrase recognized after samples=" + samples);
            if (recovery.consume(ticket)) {
              releaseCapture();
              wake.run();
            }
            break;
          }
        }
      }
    } catch (Exception | LinkageError e) {
      Log.e(
          "TVGPTWake",
          "Local wake unavailable: " + e.getClass().getSimpleName() + ": " + e.getMessage());
      releaseCapture();
      long delay = recovery.retryDelay(ticket);
      if (delay >= 0) {
        try {
          executor.schedule(() -> listen(ticket), delay, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
          // Disposal invalidated the retry while this failed recorder unwound.
        }
      }
    }
  }
}
