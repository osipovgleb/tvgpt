package local.tvgpt.assistant;

import android.content.Context;
import android.media.*;
import android.util.Log;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.*;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

/** Exactly one idle recorder. It is stopped and released before conversation capture. */
final class WakeDetector {
  private final Context context;
  private final Runnable wake;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Object lock = new Object();
  private volatile boolean wanted, closed;
  private volatile AudioRecord capture;
  private Model model;

  WakeDetector(Context c, Runnable wake) {
    context = c;
    this.wake = wake;
  }

  void start() {
    if (closed || wanted) return;
    wanted = true;
    executor.execute(this::listen);
  }

  void stop() {
    wanted = false;
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
          if (!wanted && model != null) {
            model.close();
            model = null;
          }
        });
  }

  void close() {
    closed = true;
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

  private void listen() {
    try {
      if (model == null) {
        Log.i("TVGPTWake", "Loading local Russian wake model");
        model = new Model(unpack().getPath());
      }
      if (!wanted || closed) return;
      try (Recognizer recognizer =
          new Recognizer(model, 16000, "[\"привет телевизор\",\"окей телевизор\",\"[unk]\"]")) {
        AudioRecord record;
        synchronized (lock) {
          if (!wanted || closed) return;
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
        while (wanted && !closed) {
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
          JSONObject result =
              new JSONObject(finalResult ? recognizer.getResult() : recognizer.getPartialResult());
          if (WakePhrase.matches(result.optString(finalResult ? "text" : "partial"))) {
            Log.i("TVGPTWake", "Wake phrase recognized after samples=" + samples);
            stop();
            wake.run();
            break;
          }
        }
      }
    } catch (Exception | LinkageError e) {
      Log.e(
          "TVGPTWake",
          "Local wake unavailable: " + e.getClass().getSimpleName() + ": " + e.getMessage());
      stop();
    }
  }
}
