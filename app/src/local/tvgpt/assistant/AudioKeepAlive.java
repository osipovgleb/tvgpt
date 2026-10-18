package local.tvgpt.assistant;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;

public class AudioKeepAlive extends Service {
  @Override
  public void onCreate() {
    super.onCreate();
    foreground();
  }

  private void foreground() {
    NotificationManager notifications = getSystemService(NotificationManager.class);
    notifications.createNotificationChannel(
        new NotificationChannel("voice", "Голосовой разговор", NotificationManager.IMPORTANCE_LOW));
    Notification n =
        new Notification.Builder(this, "voice")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("TV GPT")
            .setContentText("Голосовой помощник · Привет, телевизор")
            .setOngoing(true)
            .build();
    startForeground(11, n);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int id) {
    foreground();
    return START_NOT_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
