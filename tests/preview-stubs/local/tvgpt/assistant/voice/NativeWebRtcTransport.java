package local.tvgpt.assistant.voice;

public class NativeWebRtcTransport {
  public static NativeWebRtcTransport last;
  public final VoiceTransport.Listener listener;
  public final boolean playbackOnly;
  public final double gain;
  private Runnable released;

  public NativeWebRtcTransport(
      android.content.Context c, VoiceTransport.Listener l, boolean only, double gain) {
    last = this;
    listener = l;
    playbackOnly = only;
    this.gain = gain;
  }

  public void start() {}

  public void answer(String s) {}

  public void close(Runnable r) {
    released = r;
  }

  public void finishRelease() {
    released.run();
  }
}
