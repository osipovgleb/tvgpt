package local.tvgpt.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.view.*;
import android.widget.*;

/** Settings and onboarding only. Voice is always a separate system assistant popup. */
final class TechnicalSettingsView extends FrameLayout {
  private final Activity host;
  private final Runnable openAssistant;
  private TextView status, code, accountLabel;
  private View login, configure, remote;
  private DeviceLogin flow;
  private AccountStore store;
  private final local.tvgpt.assistant.ui.SettingsRows settingsRows;

  private int dp(float n) {
    return (int) (getResources().getDisplayMetrics().density * n + .5f);
  }

  TechnicalSettingsView(Activity host, Runnable openAssistant) {
    super(host);
    this.host = host;
    settingsRows = new local.tvgpt.assistant.ui.SettingsRows(host);
    this.openAssistant = openAssistant;
    store = new AccountStore(host);
    host.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    android.widget.FrameLayout root = new FrameLayout(host);
    root.setBackgroundColor(0xff0f0f0f);
    ScrollView scroll = new ScrollView(host);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    FrameLayout.LayoutParams width =
        new FrameLayout.LayoutParams(
            Math.min(getResources().getDisplayMetrics().widthPixels - dp(40), dp(620)),
            -1,
            Gravity.CENTER_HORIZONTAL);
    root.addView(scroll, width);
    addView(root, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout box = new LinearLayout(host);
    box.setOrientation(1);
    box.setPadding(dp(24), dp(22), dp(24), dp(36));
    scroll.addView(box, new ScrollView.LayoutParams(-1, -2));
    status = plain("", 13, 0xff929292);
    status.setVisibility(View.GONE);
    status.setGravity(Gravity.CENTER);
    status.setPadding(0, dp(3), 0, dp(14));
    box.addView(status);
    code = plain("", 30, 0xffffffff);
    code.setGravity(Gravity.CENTER);
    code.setVisibility(View.GONE);
    box.addView(code);
    LinearLayout general = group(box, "Общие");
    LinearLayout zone =
        row(
            general,
            SettingsGlyph.CLOCK,
            "Часовой пояс",
            local.tvgpt.assistant.ui.TimezoneSetting.label(host),
            () -> {});
    zone.setOnClickListener(
        v ->
            local.tvgpt.assistant.ui.TimezoneSetting.show(
                host,
                () ->
                    ((TextView) zone.getTag())
                        .setText(local.tvgpt.assistant.ui.TimezoneSetting.label(host))));
    accountSettings(box);
    voiceSettings(box);
    LinearLayout sound = group(box, "Звук");
    switchRow(
        sound,
        SettingsGlyph.SPEAKER,
        "Приглушать другие приложения",
        "Во время разговора с ассистентом",
        host.getSharedPreferences("assistant", 0).getBoolean("duck", true),
        enabled ->
            host.getSharedPreferences("assistant", 0).edit().putBoolean("duck", enabled).apply());
    controlSettings(box);
    LinearLayout reminders = group(box, "Напоминания");
    row(
        reminders,
        SettingsGlyph.SHIELD,
        "Точные напоминания",
        local.tvgpt.assistant.reminders.AlarmScheduler.exact(host)
            ? "Разрешены"
            : "Не разрешены — возможна задержка",
        () -> {
          try {
            host.startActivity(
                new Intent(
                    "android.settings.REQUEST_SCHEDULE_EXACT_ALARM",
                    android.net.Uri.parse("package:" + host.getPackageName())));
          } catch (Exception e) {
            showStatus("Этот экран недоступен на прошивке ТВ");
          }
        });
    logoutSettings(box);
    refresh();
  }

  private void accountSettings(LinearLayout box) {
    LinearLayout account = group(box, "Аккаунт");
    login =
        row(
            account,
            SettingsGlyph.ACCOUNT,
            "Аккаунт ChatGPT",
            "Войти по коду устройства",
            () -> beginLogin());
    accountLabel = (TextView) login.getTag();
  }

  private void voiceSettings(LinearLayout box) {
    LinearLayout voice = group(box, "Голосовой помощник");
    configure =
        row(
            voice,
            SettingsGlyph.SHIELD,
            "Заменить Google на TV GPT",
            "Назначить системным ассистентом и освободить микрофон",
            () -> configureAssistant());
    switchRow(
        voice,
        SettingsGlyph.VOICE,
        "Фоновый голосовой вызов",
        "«Привет, телевизор». Включённый режим держит микрофон и зелёную точку Android. Выключите"
            + " для запуска только с пульта.",
        AssistantService.wakeEnabled(host),
        enabled -> AssistantService.setWakeEnabled(host, enabled));
    row(
        voice,
        SettingsGlyph.VOICE,
        "Проверить помощника",
        "Проверить соединение и ответ из динамиков",
        () -> {
          if (host.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
              != android.content.pm.PackageManager.PERMISSION_GRANTED)
            host.requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, 7);
          else openAssistant.run();
        });
  }

  private void controlSettings(LinearLayout box) {
    LinearLayout control = group(box, "Управление телевизором");
    remote =
        row(
            control,
            SettingsGlyph.REMOTE,
            "Локальный пульт",
            "Подключить управление кнопками телевизора",
            () -> pairRemote());
    row(
        control,
        SettingsGlyph.SHIELD,
        "Доступ к экрану",
        "Настройки специальных возможностей",
        () -> {
          try {
            host.startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
          } catch (Exception e) {
            showStatus("Настройки ТВ → Специальные возможности → TV GPT");
          }
        });
  }

  private void logoutSettings(LinearLayout box) {
    LinearLayout logout = group(box, "");
    row(
        logout,
        SettingsGlyph.LOGOUT,
        "Выйти из аккаунта",
        "",
        () -> {
          if (flow != null) flow.cancel();
          store.clear();
          AssistantService.setWakeEnabled(host, false);
          code.setVisibility(View.GONE);
          host.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
          showStatus("Вы вышли из аккаунта");
          login.setEnabled(true);
          refresh();
        });
  }

  private TextView plain(String text, int size, int color) {
    TextView v = new TextView(host);
    v.setText(text);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setIncludeFontPadding(false);
    return v;
  }

  private LinearLayout group(LinearLayout box, String label) {
    return settingsRows.group(box, label);
  }

  private LinearLayout row(
      LinearLayout group, int kind, String title, String subtitle, Runnable action) {
    return settingsRows.row(group, kind, title, subtitle, action);
  }

  private interface Toggle {
    void changed(boolean value);
  }

  private void switchRow(
      LinearLayout group, int kind, String title, String subtitle, boolean checked, Toggle action) {
    Switch toggle = new Switch(host);
    toggle.setChecked(checked);
    toggle.setFocusable(false);
    toggle.setClickable(false);
    toggle.setShowText(false);
    LinearLayout row =
        row(group, kind, title, subtitle, () -> toggle.setChecked(!toggle.isChecked()));
    row.removeViewAt(row.getChildCount() - 1);
    row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
    toggle.setOnCheckedChangeListener((button, value) -> action.changed(value));
  }

  private void configureAssistant() {
    configure.setEnabled(false);
    showStatus("Настраиваю системного ассистента…");
    new Thread(
            () -> {
              String result;
              try {
                result =
                    AssistantSetup.configure(
                        command -> AdbRemote.shell(host.getApplicationContext(), command));
              } catch (Exception e) {
                result =
                    "Сначала подключите локальный пульт. "
                        + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
              }
              final String message = result;
              host.runOnUiThread(
                  () -> {
                    configure.setEnabled(true);
                    showStatus(message);
                    AssistantService.refreshWake();
                  });
            },
            "tvgpt-assistant-setup")
        .start();
  }

  private void pairRemote() {
    remote.setEnabled(false);
    TextView detail = (TextView) remote.getTag();
    detail.setText("Подключаю… Подтвердите отладку, если появится запрос.");
    new Thread(
            () -> {
              String result;
              try {
                String reply =
                    AdbRemote.shell(host.getApplicationContext(), "echo TVGPT_REMOTE_READY");
                if (!reply.contains("TVGPT_REMOTE_READY"))
                  throw new Exception("Телевизор не подтвердил соединение");
                result = "Подключён — управление кнопками доступно";
              } catch (Exception e) {
                result =
                    e.getMessage() == null
                        ? "Не удалось подключить локальный пульт"
                        : e.getMessage();
              }
              final String message = result;
              host.runOnUiThread(
                  () -> {
                    remote.setEnabled(true);
                    detail.setText(message);
                    Toast.makeText(host, message, Toast.LENGTH_LONG).show();
                  });
            },
            "tvgpt-remote-pairing")
        .start();
  }

  void refresh() {
    try {
      boolean connected = store.load() != null;
      accountLabel.setText(connected ? "Подключён" : "Войти по коду устройства");
    } catch (Exception e) {
      accountLabel.setText("Не удалось прочитать вход");
    }
  }

  private void showStatus(String text) {
    status.setText(text);
    status.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
  }

  private void beginLogin() {
    if (flow != null) flow.cancel();
    flow = new DeviceLogin();
    login.setEnabled(false);
    host.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    showStatus("Запрашиваю код входа…");
    code.setText("");
    code.setVisibility(View.VISIBLE);
    flow.start(
        store,
        new DeviceLogin.Listener() {
          public void code(String value) {
            host.runOnUiThread(
                () -> {
                  code.setText(value);
                  showStatus("Откройте auth.openai.com/codex/device на телефоне и введите код.");
                });
          }

          public void status(String value) {
            host.runOnUiThread(() -> showStatus(value));
          }

          public void finished(boolean success) {
            host.runOnUiThread(
                () -> {
                  login.setEnabled(true);
                  if (success) {
                    code.setVisibility(View.GONE);
                    host.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                    showStatus("Аккаунт подключён");
                    refresh();
                    AssistantService.refreshWake();
                  }
                });
          }
        });
  }

  void permissionsResult(int request, int[] grants) {
    AssistantService.refreshWake();
    if (request == 7
        && grants.length > 0
        && grants[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) openAssistant.run();
  }

  void dispose() {
    if (flow != null) flow.cancel();
    host.getWindow()
        .clearFlags(
            WindowManager.LayoutParams.FLAG_SECURE
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }
}
