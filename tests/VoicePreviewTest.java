package local.tvgpt.assistant;

import java.util.concurrent.atomic.AtomicInteger;
import local.tvgpt.assistant.settings.SpeechPreset;
import local.tvgpt.assistant.voice.NativeWebRtcTransport;

public final class VoicePreviewTest {
  public static void main(String[] args) {
    AtomicInteger selected = new AtomicInteger(), released = new AtomicInteger();
    AtomicInteger recordings = new AtomicInteger();
    VoicePreview p =
        new VoicePreview(
            new android.content.Context(),
            new local.tvgpt.assistant.voice.PlaybackRecording() {
              public void append(byte[] pcm, int rate, int channels) {}

              public void complete(boolean success) {
                if (!success) throw new AssertionError("verified preview lost");
                recordings.incrementAndGet();
              }
            });
    p.start(
        "cove",
        SpeechPreset.QUIET,
        new VoicePreview.Listener() {
          public void status(String text) {}

          public void played(String id) {
            selected.incrementAndGet();
          }
        });
    NativeWebRtcTransport t = NativeWebRtcTransport.last;
    if (!t.playbackOnly || t.gain != .35)
      throw new AssertionError("preview enabled capture or changed gain");
    t.listener.event("{\"type\":\"turn.done\",\"turn\":{\"role\":\"assistant\"}}");
    if (selected.get() != 0) throw new AssertionError("selected without received audio");
    t.listener.playback();
    if (selected.get() != 1) throw new AssertionError("verified preview");
    p.stop(released::incrementAndGet);
    p.stop(released::incrementAndGet);
    if (recordings.get() != 0 || released.get() != 0 || !VoicePreview.isActive())
      throw new AssertionError("close callbacks ran before native release");
    t.listener.playback();
    t.listener.event("{\"type\":\"turn.done\",\"turn\":{\"role\":\"assistant\"}}");
    if (selected.get() != 1) throw new AssertionError("late callback changed voice");
    t.finishRelease();
    if (recordings.get() != 1
        || released.get() != 2
        || VoicePreview.isActive()
        || AssistantService.owner != null) throw new AssertionError("release ordering");
    System.out.println(
        "Voice preview: playback only, audio verification, late callbacks and release ordering"
            + " passed");
  }
}
