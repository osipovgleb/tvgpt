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
  private final java.util.function.Consumer<String> completion;
  private final BroadcastReceiver screenOff =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          close();
        }
      };

  private ReminderSpeech(Context c, java.util.function.Consumer<String> completion) {
    context = c.getApplicationContext();
    this.completion = completion;
  }

  static void speak(Context c, String text, java.util.function.Consumer<String> completion) {
    new Handler(Looper.getMainLooper()).post(() -> new ReminderSpeech(c, completion).start(text));
  }

  private void start(String text) {
    if (!ReminderDispatcher.interactive(context)) {
      finish("failed");
      return;
    }
    main.postDelayed(this::close, 20000);
    try {
      context.registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
      engine =
          new TextToSpeech(
              context,
              result ->
                  main.post(
                      () -> {
                        try {
                          ready(result, text);
                        } catch (RuntimeException e) {
                          finish("failed");
                        }
                      }));
    } catch (RuntimeException e) {
      finish("failed");
    }
  }

  private void ready(int result, String text) {
    if (closed) return;
    if (result != TextToSpeech.SUCCESS || !ReminderDispatcher.interactive(context)) {
      finish("failed");
      return;
    }
    int language = engine.setLanguage(new Locale("ru", "RU"));
    if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
      finish("failed");
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
            main.post(
                () ->
                    finish(ReminderDispatcher.interactive(context) ? "completed" : "unconfirmed"));
          }

          public void onError(String id) {
            main.post(() -> finish("failed"));
          }
        });
    if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reminder") == TextToSpeech.ERROR)
      finish("failed");
  }

  private void close() {
    finish("unconfirmed");
  }

  private void finish(String outcome) {
    if (closed) return;
    closed = true;
    main.removeCallbacksAndMessages(null);
    try {
      context.unregisterReceiver(screenOff);
    } catch (IllegalArgumentException ignored) {
    }
    if (engine != null) {
      try {
        engine.stop();
        engine.shutdown();
      } catch (RuntimeException e) {
        android.util.Log.w("TVGPTReminders", "TTS release unavailable", e);
      }
      engine = null;
    }
    completion.accept(outcome);
  }
}
