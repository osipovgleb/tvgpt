package android.app;

public final class NotificationChannel {
  public NotificationChannel(String a, String b, int c) {}

  public int getImportance() {
    return NotificationManager.enabled ? 1 : 0;
  }
}
