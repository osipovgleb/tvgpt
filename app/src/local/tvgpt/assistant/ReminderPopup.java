package local.tvgpt.assistant;

import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.view.*;
import android.widget.*;
import local.tvgpt.assistant.ui.Screens;

/** Non-focusable reminder card; never starts a voice session or wakes the screen. */
public final class ReminderPopup {
  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static ReminderPopup active;
  private final TvAccessibility owner;
  private final WindowManager windows;
  private final LinearLayout card;
  private boolean attached, registered;
  private final Runnable timeout = this::close;
  private final BroadcastReceiver screen =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          close();
        }
      };

  private ReminderPopup(TvAccessibility service, String message) {
    owner = service;
    windows = service.getSystemService(WindowManager.class);
    card = new LinearLayout(service);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(
        Screens.dp(service, 22),
        Screens.dp(service, 16),
        Screens.dp(service, 22),
        Screens.dp(service, 18));
    card.setBackground(Screens.rounded(service, 0xff242428, 20));
    TextView title = Screens.text(service, "Напоминание", 20);
    card.addView(title);
    TextView body = Screens.text(service, message, 16);
    body.setMaxLines(6);
    body.setEllipsize(android.text.TextUtils.TruncateAt.END);
    card.addView(body);
  }

  public static void show(String message) {
    MAIN.post(
        () -> {
          if (active != null) active.close();
          TvAccessibility service = TvAccessibility.current;
          if (service == null || !service.getSystemService(PowerManager.class).isInteractive())
            return;
          ReminderPopup popup = new ReminderPopup(service, message);
          active = popup;
          popup.open();
        });
  }

  static void dismiss(TvAccessibility service) {
    if (active != null && active.owner == service) active.close();
  }

  private void open() {
    WindowManager.LayoutParams p =
        new WindowManager.LayoutParams(
            Math.min(
                Screens.dp(owner, 340),
                owner.getResources().getDisplayMetrics().widthPixels - Screens.dp(owner, 48)),
            -2,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT);
    p.gravity = Gravity.TOP | Gravity.RIGHT;
    p.x = p.y = Screens.dp(owner, 24);
    try {
      owner.registerReceiver(screen, new IntentFilter(Intent.ACTION_SCREEN_OFF));
      registered = true;
      windows.addView(card, p);
      attached = true;
      MAIN.postDelayed(timeout, 15000);
    } catch (RuntimeException e) {
      android.util.Log.w("TVGPTReminders", "Reminder card unavailable", e);
      close();
    }
  }

  private void close() {
    MAIN.removeCallbacks(timeout);
    if (attached) {
      try {
        windows.removeViewImmediate(card);
      } catch (IllegalArgumentException ignored) {
      }
      attached = false;
    }
    if (registered) {
      owner.unregisterReceiver(screen);
      registered = false;
    }
    if (active == this) active = null;
  }
}
