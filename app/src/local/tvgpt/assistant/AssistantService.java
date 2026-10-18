package local.tvgpt.assistant;

import android.content.*;
import android.os.*;
import android.service.voice.VoiceInteractionService;

public class AssistantService extends VoiceInteractionService {
  static volatile AssistantService current;

  @Override
  protected void dump(java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {
    // Android's ActivityManager guards dumpsys service access with the DUMP permission.
    // Text and bounded task receipts only: no account, microphone audio or screen images.
    writer.println(LiveConnection.diagnostics());
  }

  private static final java.util.concurrent.atomic.AtomicBoolean protectionAttempted =
      new java.util.concurrent.atomic.AtomicBoolean();
  private WakeDetector detector;
  private BroadcastReceiver screenReceiver;
  private volatile Object conversation;
  private final Handler main = new Handler(Looper.getMainLooper());

  static boolean wakeEnabled(Context c) {
    return c.getSharedPreferences("assistant", 0).getBoolean("wake", true);
  }

  static void stopWake() {
    AssistantService s = current;
    if (s != null && s.detector != null) s.detector.stop();
  }

  static void refreshWake() {
    AssistantService s = current;
    if (s != null) s.main.post(s::refresh);
  }

  static void setWakeEnabled(Context c, boolean enabled) {
    c.getSharedPreferences("assistant", 0).edit().putBoolean("wake", enabled).apply();
    AssistantService service = current;
    if (service != null) service.main.post(service::refresh);
  }

  @Override
  public void onReady() {
    super.onReady();
    current = this;
    if (protectionAttempted.compareAndSet(false, true))
      new Thread(
              () -> {
                try {
                  String warning =
                      AssistantSetup.protect(
                          command -> AdbRemote.shell(getApplicationContext(), command));
                  if (!warning.isEmpty())
                    android.util.Log.w("TVGPTStartup", "OEM process protection not confirmed");
                } catch (Exception unavailable) {
                  android.util.Log.i(
                      "TVGPTStartup",
                      "OEM protection unavailable: " + unavailable.getClass().getSimpleName());
                }
              },
              "tvgpt-startup-protection")
          .start();
    if (screenReceiver == null) {
      screenReceiver =
          new BroadcastReceiver() {
            public void onReceive(Context c, Intent i) {
              local.tvgpt.assistant.reminders.ReminderDispatcher.onInteractive(c);
            }
          };
      registerReceiver(screenReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }
    local.tvgpt.assistant.reminders.ReminderDispatcher.onProcessStart(this);
    local.tvgpt.assistant.reminders.ReminderDispatcher.onInteractive(this);
    // Android can rebind the system service while its accessibility overlay survives.
    // Adopt that conversation before starting any idle microphone capture.
    conversation = VoicePanel.active != null ? VoicePanel.active : VoicePreview.owner();
    if (detector == null)
      detector =
          new WakeDetector(
              this,
              () ->
                  main.post(
                      () -> {
                        if (current == this
                            && conversation == null
                            && wakeEnabled(this)
                            && hasAccount()) {
                          Bundle args = new Bundle();
                          args.putString("source", "wake");
                          showSession(args, 0);
                        }
                      }));
    refresh();
  }

  private void refresh() {
    if (current != this || detector == null) return;
    boolean account = hasAccount();
    if (wakeEnabled(this)
        && account
        && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
      startForegroundService(new Intent(this, AudioKeepAlive.class));
      if (conversation == null) detector.start();
    } else {
      detector.releaseIdleModel();
      if (conversation == null) stopService(new Intent(this, AudioKeepAlive.class));
    }
  }

  private boolean hasAccount() {
    try {
      return new AccountStore(this).load() != null;
    } catch (Exception ignored) {
      return false;
    }
  }

  static void conversationStarted(Object owner) {
    AssistantService s = current;
    if (s != null) {
      s.conversation = owner;
      if (s.detector != null) s.detector.stop();
    }
  }

  static void conversationEnded(Object owner) {
    AssistantService s = current;
    if (s != null && s.conversation == owner) {
      s.conversation = null;
      s.main.postDelayed(s::refresh, 800);
    }
  }

  @Override
  public void onShutdown() {
    if (screenReceiver != null) {
      unregisterReceiver(screenReceiver);
      screenReceiver = null;
    }
    if (current == this) current = null;
    if (detector != null) {
      detector.close();
      detector = null;
    }
    if (conversation == null) stopService(new Intent(this, AudioKeepAlive.class));
    super.onShutdown();
  }
}
