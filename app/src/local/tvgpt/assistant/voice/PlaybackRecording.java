package local.tvgpt.assistant.voice;

/** Explicitly requested synthetic voice samples only; never microphone input. */
public interface PlaybackRecording {
  void append(byte[] pcm, int sampleRate, int channels);

  void complete(boolean successful);
}
