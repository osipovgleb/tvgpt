package local.tvgpt.assistant;

import android.content.Context;
import android.media.*;

final class SessionSounds {
  static void ready(Context context) {
    MediaPlayer player = new MediaPlayer();
    try (android.content.res.AssetFileDescriptor source =
        context.getAssets().openFd("voice-start.ogg")) {
      player.setAudioAttributes(
          new AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_ASSISTANT)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build());
      player.setDataSource(source.getFileDescriptor(), source.getStartOffset(), source.getLength());
      player.setVolume(.7f, .7f);
      player.setOnPreparedListener(MediaPlayer::start);
      player.setOnCompletionListener(MediaPlayer::release);
      player.setOnErrorListener(
          (p, what, extra) -> {
            p.release();
            return true;
          });
      player.prepareAsync();
    } catch (Exception e) {
      player.release();
      android.util.Log.w("TVGPTAudio", "Startup cue unavailable: " + e.getClass().getSimpleName());
    }
  }
}
