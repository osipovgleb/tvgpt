package local.tvgpt.assistant.voice;

public interface VoiceTransport {
  interface Listener {
    void offer(String sdp);

    void event(String raw);

    void ready();

    default void playback() {}

    default void playbackSamples(byte[] pcm, int sampleRate, int channels) {}

    void level(double value);

    void failure(String message);
  }

  void start();

  void answer(String sdp);

  void setMuted(boolean muted);

  void close(Runnable released);
}
