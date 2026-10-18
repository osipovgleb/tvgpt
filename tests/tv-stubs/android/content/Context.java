package android.content;

public class Context {
  public final android.media.AudioManager audio = new android.media.AudioManager();

  public Context() {}

  public android.content.pm.ApplicationInfo getApplicationInfo() {
    android.content.pm.ApplicationInfo info = new android.content.pm.ApplicationInfo();
    info.sourceDir = "/test/tvgpt.apk";
    return info;
  }

  public Object getSystemService(String name) {
    return audio;
  }
}
