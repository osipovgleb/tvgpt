package local.tvgpt.assistant.ui;

import android.app.*;
import android.content.Context;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;
import local.tvgpt.assistant.SettingsGlyph;
import local.tvgpt.assistant.data.*;
import local.tvgpt.assistant.digests.*;
import org.json.*;

/** One right-hand editor, with remote-first time and weekday controls. */
public final class DigestSheet {
  private DigestSheet() {}

  public static void open(Context c, String kind, String id, Runnable changed) {
    open(c, kind, id, "", changed);
  }

  public static void open(Context c, String kind, String id, String digestId, Runnable changed) {
    AsyncScreen.IO.submit(
        () -> {
          try {
            JSONObject data =
                id.isEmpty() ? new JSONObject() : new DigestRepository(c).read(kind, id);
            new android.os.Handler(android.os.Looper.getMainLooper())
                .post(
                    () -> {
                      if (c instanceof Activity && ((Activity) c).isFinishing()) return;
                      new Editor(c, kind, data == null ? new JSONObject() : data, digestId, changed)
                          .show();
                    });
          } catch (Exception e) {
            new android.os.Handler(android.os.Looper.getMainLooper())
                .post(() -> Toast.makeText(c, "Не удалось открыть", Toast.LENGTH_SHORT).show());
          }
        });
  }

  private static final class Editor {
    final Context c;
    final String kind, parent;
    final JSONObject data;
    final Runnable changed;
    final EditText title, topics, url;
    final LinearLayout box;
    final TextView error;
    final String operation = UUID.randomUUID().toString();
    boolean enabled, screensaver;
    int mode, sourceType, repeat, weekdays, interval;
    LocalTime time;

    Editor(Context c, String kind, JSONObject data, String parent, Runnable changed) {
      this.c = c;
      this.kind = kind;
      this.data = data;
      this.parent = data.optString("digest_id", parent);
      this.changed = changed;
      enabled = data.optBoolean("enabled", true);
      screensaver = data.optBoolean("screensaver", true);
      mode = data.optString("source_mode", "open").equals("selected") ? 1 : 0;
      sourceType = Arrays.asList("rss", "atom", "web").indexOf(data.optString("type", "rss"));
      if (sourceType < 0) sourceType = 0;
      repeat =
          Arrays.asList("daily", "weekly", "interval").indexOf(data.optString("repeat", "daily"));
      if (repeat < 0) repeat = 0;
      weekdays = data.optInt("weekdays", 127);
      interval = data.optInt("interval_minutes", 60);
      time = LocalTime.of(data.optInt("hour", 9), data.optInt("minute", 0));
      box = Screens.column(c, "");
      title = input("Название", data.optString("title"));
      topics = input("Темы новостей", data.optString("topics"));
      url = input("HTTPS адрес сайта или RSS", data.optString("url"));
      error = Screens.text(c, "", 13);
    }

    EditText input(String hint, String value) {
      EditText t = new EditText(c);
      t.setHint(hint);
      t.setText(value);
      t.setTextColor(0xffeeeeee);
      t.setHintTextColor(0xff999999);
      t.setSingleLine(true);
      return t;
    }

    void show() {
      if (!kind.equals("schedule")) box.addView(title);
      if (kind.equals("digest")) {
        box.addView(topics);
        box.addView(
            new ArrowRow(
                c,
                "Источники",
                () -> mode == 0 ? "Сайты и интернет" : "Только выбранные",
                d -> mode = 1 - mode));
        toggle("Показывать в заставке", true);
      }
      if (kind.equals("source")) {
        box.addView(url);
        box.addView(
            new ArrowRow(
                c,
                "Тип",
                () -> new String[] {"RSS", "Atom", "Сайт"}[sourceType],
                d -> sourceType = Math.floorMod(sourceType + d, 3)));
      }
      if (kind.equals("schedule")) schedule();
      toggle("Включено", false);
      box.addView(error);
      AlertDialog dialog =
          new RightSheet.Builder(c)
              .setTitle(
                  kind.equals("digest")
                      ? "Подборка"
                      : kind.equals("source") ? "Источник" : "Расписание")
              .setView(Screens.scroll(c, box))
              .setPositiveButton("Сохранить", null)
              .setNegativeButton("Отмена", null)
              .create();
      if (data.has("id"))
        dialog.setButton(
            AlertDialog.BUTTON_NEUTRAL,
            "Удалить",
            (d, w) ->
                new RightSheet.Builder(c)
                    .setMessage("Удалить?")
                    .setPositiveButton("Удалить", (confirm, which) -> save(dialog, "delete"))
                    .setNegativeButton("Отмена", null)
                    .show());
      dialog.setOnShowListener(
          v ->
              dialog
                  .getButton(-1)
                  .setOnClickListener(
                      button -> save(dialog, data.has("id") ? "update" : "create")));
      RightSheet.show(dialog);
    }

    void toggle(String label, boolean screen) {
      SettingsRows rows = new SettingsRows(c);
      LinearLayout group = rows.group(box, "");
      final LinearLayout[] row = new LinearLayout[1];
      row[0] =
          rows.row(
              group,
              SettingsGlyph.SETTINGS,
              label,
              (screen ? screensaver : enabled) ? "Да" : "Нет",
              () -> {
                if (screen) screensaver = !screensaver;
                else enabled = !enabled;
                ((TextView) row[0].getTag())
                    .setText((screen ? screensaver : enabled) ? "Да" : "Нет");
              });
      row[0].setOnKeyListener(
          (v, key, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                && (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT)) {
              v.performClick();
              return true;
            }
            return false;
          });
    }

    void schedule() {
      box.addView(RemoteTimePicker.view(c, time, value -> time = value));
      LinearLayout days = new LinearLayout(c);
      days.setGravity(Gravity.CENTER);
      box.addView(
          new ArrowRow(
              c,
              "Повтор",
              () -> new String[] {"Ежедневно", "По дням недели", "Интервал"}[repeat],
              direction -> {
                repeat = Math.floorMod(repeat + direction, 3);
                days.setVisibility(repeat == 1 ? View.VISIBLE : View.GONE);
              }));
      String[] labels = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
      for (int i = 0; i < 7; i++) {
        final int mask = 1 << i;
        ToggleButton b = new ToggleButton(c);
        b.setTextOn(labels[i]);
        b.setTextOff(labels[i]);
        b.setText(labels[i]);
        b.setChecked((weekdays & mask) != 0);
        b.setSingleLine(true);
        b.setTextSize(12);
        b.setTextColor(0xffeeeeee);
        b.setPadding(0, 0, 0, 0);
        b.setMinimumHeight(0);
        b.setMinimumWidth(0);
        StateListDrawable state = new StateListDrawable();
        GradientDrawable focus = Screens.rounded(c, 0xff55555c, 18);
        state.addState(new int[] {android.R.attr.state_focused}, focus);
        state.addState(
            new int[] {android.R.attr.state_checked}, Screens.rounded(c, 0xff484850, 18));
        state.addState(new int[] {}, Screens.rounded(c, 0xff242428, 18));
        b.setBackground(state);
        b.setOnCheckedChangeListener(
            (button, on) -> weekdays = on ? weekdays | mask : weekdays & ~mask);
        FrameLayout cell = new FrameLayout(c);
        cell.addView(
            b, new FrameLayout.LayoutParams(Screens.dp(c, 36), Screens.dp(c, 36), Gravity.CENTER));
        days.addView(cell, new LinearLayout.LayoutParams(0, Screens.dp(c, 46), 1));
      }
      days.setVisibility(repeat == 1 ? View.VISIBLE : View.GONE);
      box.addView(days);
      box.addView(
          new ArrowRow(
              c,
              "Интервал",
              () -> interval / 60 + " ч",
              direction -> interval = Math.max(60, Math.min(10080, interval + direction * 60))));
      String zone =
          data.optString(
              "zone", new local.tvgpt.assistant.settings.GeneralPreferences(c).zone().getId());
      box.addView(Screens.text(c, zone, 12));
    }

    void save(AlertDialog dialog, String action) {
      try {
        JSONObject args =
            new JSONObject()
                .put("operation_id", operation)
                .put("action", action)
                .put("id", data.optString("id"))
                .put("expected_revision", data.optLong("revision", 0))
                .put("enabled", enabled);
        String tool;
        if (kind.equals("digest")) {
          tool = "digest_mutate";
          args.put("title", title.getText().toString())
              .put("topics", topics.getText().toString())
              .put("source_mode", mode == 0 ? "open" : "selected")
              .put("screensaver", screensaver);
        } else if (kind.equals("source")) {
          tool = "digest_source_mutate";
          args.put("digest_id", parent)
              .put("title", title.getText().toString())
              .put("url", url.getText().toString())
              .put("type", new String[] {"rss", "atom", "web"}[sourceType]);
        } else {
          tool = "digest_schedule_mutate";
          args.put("digest_id", parent)
              .put("repeat", new String[] {"daily", "weekly", "interval"}[repeat])
              .put("hour", time.getHour())
              .put("minute", time.getMinute())
              .put("weekdays", weekdays)
              .put("interval_minutes", interval);
        }
        dialog.getButton(-1).setEnabled(false);
        AsyncScreen.IO.submit(
            () -> {
              try {
                JSONObject result = new DigestCommands(c).execute(tool, args);
                if (result.optString("status").equals("conflict"))
                  throw new Exception("Данные изменились. Откройте редактор заново");
                new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(
                        () -> {
                          dialog.dismiss();
                          changed.run();
                        });
              } catch (Exception e) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(
                        () -> {
                          if (!dialog.isShowing()) return;
                          error.setText(
                              e.getMessage() == null ? "Не удалось сохранить" : e.getMessage());
                          dialog.getButton(-1).setEnabled(true);
                        });
              }
            });
      } catch (Exception e) {
        error.setText("Проверьте поля");
      }
    }
  }
}
