package local.tvgpt.assistant;

import android.content.Context;
import android.os.*;
import local.tvgpt.assistant.settings.SpeechPreset;

/** Bundled official Russian previews; no microphone, network or Live session needed. */
public final class VoiceSamples {
  public interface Listener {
    void status(String message);

    void ready(String voice);

    void unavailable(String voice);
  }

  private final Context context;
  private final Handler main = new Handler(Looper.getMainLooper());
  private int generation;

  public VoiceSamples(Context c) {
    context = c.getApplicationContext();
  }

  public void stop() {
    generation++;
    VoicePreview.stopActive(null);
  }

  public void play(String voice, SpeechPreset preset, Listener listener) {
    if (!voice.matches("arbor|breeze|cove|ember|juniper|maple|sol|spruce|vale")) {
      stop();
      listener.status("Нет образца этого голоса");
      return;
    }
    stop();
    int attempt = generation;
    VoicePreview.stopActive(
        () ->
            main.post(
                () -> {
                  if (attempt != generation) return;
                  listener.status("");
                  android.util.Log.i("TVGPTPreview", "Bundled sample voice=" + voice);
                  VoiceSamplePlayer.play(
                      context,
                      "voice-samples/" + voice + ".m4a",
                      preset.outputGain,
                      ok -> {
                        if (attempt != generation || ok == null) return;
                        if (ok) listener.ready(voice);
                        else listener.status("Образец не воспроизвёлся");
                      });
                }));
  }
}
