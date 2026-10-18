package android.app;

public final class NotificationManager {
  public static final int IMPORTANCE_DEFAULT = 1, IMPORTANCE_NONE = 0;
  public static boolean enabled = true;
  public static final java.util.List<String> bodies = new java.util.ArrayList<>();

  public void createNotificationChannel(NotificationChannel c) {}

  public NotificationChannel getNotificationChannel(String s) {
    return null;
  }

  public boolean areNotificationsEnabled() {
    return enabled;
  }

  public void notify(String tag, int id, Notification n) {
    bodies.add(n.text);
  }
}
