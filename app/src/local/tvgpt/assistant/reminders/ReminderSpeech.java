package local.tvgpt.assistant.reminders;

import android.content.*;
import android.media.AudioAttributes;
import android.os.*;
import android.speech.tts.*;
import java.util.Locale;

/** Transient offline fallback; never opens the assistant or captures audio. */
final class ReminderSpeech {
  private final Context context;
  private final Handler main = new Handler(Looper.getMainLooper());
  private TextToSpeech engine;
  private boolean closed;
  private final BroadcastReceiver screenOff =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          close();
        }
      };

  private ReminderSpeech(Context c) {
    context = c.getApplicationContext();
  }

  static void speak(Context c, String text) {
    new Handler(Looper.getMainLooper()).post(() -> new ReminderSpeech(c).start(text));
  }

  private void start(String text) {
    if (!ReminderDispatcher.interactive(context)) return;
    context.registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    main.postDelayed(this::close, 20000);
    try {
      engine = new TextToSpeech(context, result -> main.post(() -> ready(result, text)));
    } catch (RuntimeException e) {
      close();
    }
  }

  private void ready(int result, String text) {
    if (closed) return;
    if (result != TextToSpeech.SUCCESS || !ReminderDispatcher.interactive(context)) {
      close();
      return;
    }
    int language = engine.setLanguage(new Locale("ru", "RU"));
    if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
      close();
      return;
    }
    engine.setAudioAttributes(
        new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build());
    engine.setOnUtteranceProgressListener(
        new UtteranceProgressListener() {
          public void onStart(String id) {}

          public void onDone(String id) {
            main.post(ReminderSpeech.this::close);
          }

          public void onError(String id) {
            main.post(ReminderSpeech.this::close);
          }
        });
    engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reminder");
  }

  private void close() {
    if (closed) return;
    closed = true;
    main.removeCallbacksAndMessages(null);
    try {
      context.unregisterReceiver(screenOff);
    } catch (IllegalArgumentException ignored) {
    }
    if (engine != null) {
      engine.stop();
      engine.shutdown();
      engine = null;
    }
  }
}
