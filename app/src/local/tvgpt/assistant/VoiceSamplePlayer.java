package local.tvgpt.assistant;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import java.util.function.Consumer;

/** Local synthetic sample playback. Release before assistant capture can restart. */
final class VoiceSamplePlayer {
  private static VoiceSamplePlayer active;
  private final MediaPlayer player = new MediaPlayer();
  private final Consumer<Boolean> finished;
  private boolean closed;

  private VoiceSamplePlayer(Consumer<Boolean> finished) {
    this.finished = finished;
  }

  static Object owner() {
    return active;
  }

  static void stopActive() {
    if (active != null) active.close(null);
  }

  static void play(Context c, String asset, double gain, Consumer<Boolean> finished) {
    stopActive();
    if (VoicePanel.active != null) {
      finished.accept(null);
      return;
    }
    VoiceSamplePlayer sample = new VoiceSamplePlayer(finished);
    active = sample;
    AssistantService.conversationStarted(sample);
    try {
      sample.player.setAudioAttributes(
          new AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_ASSISTANT)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build());
      try (AssetFileDescriptor file = c.getAssets().openFd(asset)) {
        sample.player.setDataSource(
            file.getFileDescriptor(), file.getStartOffset(), file.getLength());
      }
      sample.player.setVolume((float) gain, (float) gain);
      sample.player.setOnCompletionListener(p -> sample.close(true));
      sample.player.setOnErrorListener(
          (p, what, extra) -> {
            sample.close(false);
            return true;
          });
      sample.player.setOnPreparedListener(
          p -> {
            if (!sample.closed) p.start();
          });
      sample.player.prepareAsync();
    } catch (Exception e) {
      sample.close(false);
    }
  }

  private void close(Boolean success) {
    if (closed) return;
    closed = true;
    player.release();
    if (active == this) active = null;
    AssistantService.conversationEnded(this);
    finished.accept(success);
  }
}
