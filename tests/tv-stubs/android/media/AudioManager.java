package android.media;

public final class AudioManager {
  public int mutations;

  public void adjustStreamVolume(int stream, int direction, int flags) {
    mutations++;
  }

  public void setStreamVolume(int stream, int volume, int flags) {
    mutations++;
  }

  public int getStreamVolume(int stream) {
    return 10;
  }

  public int getStreamMaxVolume(int stream) {
    return 100;
  }

  public boolean isStreamMute(int stream) {
    return false;
  }
}
