package local.tvgpt.assistant;

import android.content.Context;
import android.media.*;

/** System-managed per-player ducking. Never changes a shared stream's volume. */
final class AudioDucking {
  private final Context context;
  private final AudioManager audio;
  private AudioFocusRequest request;
  private final AudioFocusLease lease = new AudioFocusLease();
  private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
  private final Runnable reclaim = this::reclaimFocus;

  AudioDucking(Context c) {
    context = c.getApplicationContext();
    audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
  }

  void begin() {
    if (request != null || !context.getSharedPreferences("assistant", 0).getBoolean("duck", true))
      return;
    lease.open();
    request =
        new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
            .setOnAudioFocusChangeListener(this::focusChanged, main)
            .build();
    requestFocus();
  }

  private void focusChanged(int change) {
    if (change == AudioManager.AUDIOFOCUS_GAIN) main.removeCallbacks(reclaim);
    if (change == AudioManager.AUDIOFOCUS_LOSS
        || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
      long delay = lease.delay(android.os.SystemClock.elapsedRealtime());
      if (delay >= 0) {
        main.removeCallbacks(reclaim);
        main.postDelayed(reclaim, Math.max(150, delay));
      }
    }
  }

  private void reclaimFocus() {
    if (lease.reclaim(android.os.SystemClock.elapsedRealtime())) requestFocus();
  }

  private void requestFocus() {
    if (request == null) return;
    int result = audio.requestAudioFocus(request);
    android.util.Log.i("TVGPTAudio", "Background duck focus=" + result);
  }

  void close() {
    lease.close();
    main.removeCallbacks(reclaim);
    if (request != null) {
      audio.abandonAudioFocusRequest(request);
      request = null;
    }
  }
}
