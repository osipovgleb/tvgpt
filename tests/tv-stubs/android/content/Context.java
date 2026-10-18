package android.content;

public class Context {
  public final android.media.AudioManager audio = new android.media.AudioManager();

  public Context() {}

  public Object getSystemService(String name) {
    return audio;
  }
}
