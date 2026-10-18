package local.tvgpt.assistant.ui;

import android.app.*;
import android.content.Context;
import android.os.*;
import android.widget.*;
import java.time.*;
import java.time.format.*;
import local.tvgpt.assistant.data.ReminderRepository;
import local.tvgpt.assistant.reminders.*;
import org.json.*;

/** Remote-first calendar and independent hour/minute fields. */
final class ReminderEditor {
  private final Context context;
  private final boolean timer;
  private final JSONObject existing;
  private final Runnable saved;
  private final EditText body, minutes;
  private final TextView error;
  private final ArrowRow repeat;
  private LinearLayout days;
  private final ToggleButton[] dayButtons = new ToggleButton[7];
  private boolean updatingDays;
  private int repeatIndex;
  private static final String[] REPEATS = {"Без повтора", "Ежедневно", "По дням недели"};
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ZoneId zone;
  private LocalDateTime selected;
  private int weekdays;
  private boolean weekdaysEdited, dismissed;

  ReminderEditor(Context c, boolean timer, JSONObject item, Runnable saved) {
    context = c;
    this.timer = timer;
    existing = item;
    this.saved = saved;
    zone =
        item == null
            ? new local.tvgpt.assistant.settings.GeneralPreferences(c).zone()
            : ZoneId.of(item.optString("zone"));
    selected =
        item == null
            ? LocalDateTime.now(zone).plusHours(1).withSecond(0).withNano(0)
            : Instant.ofEpochMilli(item.optLong("due"))
                .atZone(zone)
                .toLocalDateTime()
                .withSecond(0)
                .withNano(0);
    weekdays = item == null ? dayMask() : item.optInt("weekdays", dayMask());
    weekdaysEdited = item != null;
    body = new EditText(c);
    body.setHint("О чём напомнить");
    if (item != null) body.setText(item.optString("body"));
    minutes = new EditText(c);
    minutes.setHint("Через сколько минут");
    minutes.setInputType(2);
    error = Screens.text(c, "", 13);
    if (item != null)
      repeatIndex =
          item.optString("repeat").equals("DAILY")
              ? 1
              : item.optString("repeat").equals("WEEKLY") ? 2 : 0;
    repeat =
        new ArrowRow(
            c,
            "Повтор",
            () -> REPEATS[repeatIndex],
            direction -> {
              repeatIndex = Math.floorMod(repeatIndex + direction, REPEATS.length);
              updateDays();
            });
  }

  void show() {
    LinearLayout box = Screens.column(context, "");
    box.addView(body);
    if (timer) box.addView(minutes);
    else calendarFields(box);
    box.addView(error);
    AlertDialog dialog =
        new RightSheet.Builder(context)
            .setTitle(existing != null ? "Изменить напоминание" : timer ? "Таймер" : "Напоминание")
            .setView(Screens.scroll(context, box))
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .create();
    dialog.setOnDismissListener(v -> dismissed = true);
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    button -> {
                      String text = body.getText().toString(),
                          value = timer ? minutes.getText().toString() : selected.toString();
                      int option = repeatIndex, mask = weekdays;
                      dialog.getButton(-1).setEnabled(false);
                      AsyncScreen.IO.submit(() -> save(dialog, text, value, option, mask));
                    }));
    RightSheet.show(dialog);
  }

  private int dayMask() {
    return 1 << (selected.getDayOfWeek().getValue() - 1);
  }

  private void calendarFields(LinearLayout box) {
    box.addView(
        new RemoteDatePicker(
                context,
                selected.toLocalDate(),
                value -> {
                  selected = selected.with(value);
                  if (!weekdaysEdited) {
                    weekdays = dayMask();
                    updateDays();
                  }
                })
            .view());
    box.addView(
        RemoteTimePicker.view(
            context, selected.toLocalTime(), value -> selected = selected.with(value)));
    box.addView(repeat);
    days = new LinearLayout(context);
    days.setGravity(android.view.Gravity.CENTER_VERTICAL);
    days.setPadding(0, Screens.dp(context, 10), 0, 0);
    String[] labels = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
    for (int i = 0; i < 7; i++) addDay(labels[i], i);
    box.addView(days);
    updateDays();
  }

  private void addDay(String label, int index) {
    ToggleButton button = new ToggleButton(context);
    dayButtons[index] = button;
    button.setTextOn(label);
    button.setTextOff(label);
    button.setText(label);
    button.setTextSize(12);
    button.setTextColor(0xffeeeeee);
    button.setPadding(0, 0, 0, 0);
    button.setSingleLine(true);
    button.setMinimumWidth(0);
    button.setMinimumHeight(0);
    button.setMinWidth(0);
    button.setMinHeight(0);
    button.setStateListAnimator(null);
    android.graphics.drawable.StateListDrawable bg =
        new android.graphics.drawable.StateListDrawable();
    android.graphics.drawable.GradientDrawable focus = Screens.rounded(context, 0xff515157, 20);
    focus.setStroke(Screens.dp(context, 1), 0xffeeeeee);
    bg.addState(new int[] {android.R.attr.state_focused}, focus);
    bg.addState(new int[] {android.R.attr.state_checked}, Screens.rounded(context, 0xff5a5a62, 20));
    bg.addState(new int[] {}, Screens.rounded(context, 0xff2a2a30, 20));
    button.setBackground(bg);
    FrameLayout slot = new FrameLayout(context);
    days.addView(slot, new LinearLayout.LayoutParams(0, Screens.dp(context, 40), 1f));
    slot.addView(
        button,
        new FrameLayout.LayoutParams(
            Screens.dp(context, 36), Screens.dp(context, 36), android.view.Gravity.CENTER));
    button.setOnCheckedChangeListener(
        (v, checked) -> {
          if (updatingDays) return;
          if (checked) weekdays |= 1 << index;
          else weekdays &= ~(1 << index);
          weekdaysEdited = true;
          repeatIndex = 2;
          repeat.refresh();
          button.setContentDescription(label + (checked ? ": выбран" : ": не выбран"));
        });
  }

  private void updateDays() {
    if (days == null) return;
    days.setVisibility(repeatIndex == 0 ? android.view.View.GONE : android.view.View.VISIBLE);
    updatingDays = true;
    if (repeatIndex == 1) weekdays = 127;
    for (int i = 0; i < 7; i++) {
      dayButtons[i].setChecked((weekdays & (1 << i)) != 0);
      dayButtons[i].setContentDescription(
          dayButtons[i].getText() + ((weekdays & (1 << i)) != 0 ? ": выбран" : ": не выбран"));
    }
    updatingDays = false;
  }

  private void save(AlertDialog dialog, String text, String value, int option, int days) {
    try {
      long due, elapsed = 0;
      if (timer) {
        long seconds = Math.multiplyExact(Long.parseLong(value), 60);
        elapsed = SchedulePolicy.timer(seconds, SystemClock.elapsedRealtime());
        due = System.currentTimeMillis() + seconds * 1000;
      } else due = SchedulePolicy.calendar(value, zone);
      String r = option == 1 ? "DAILY" : option == 2 ? "WEEKLY" : "NONE";
      ReminderRepository repo = new ReminderRepository(context);
      if (existing == null)
        repo.create(
            text,
            due,
            zone.getId(),
            timer ? "TIMER" : "REMINDER",
            timer ? "NONE" : r,
            days,
            elapsed,
            AlarmScheduler.boot(context),
            "");
      else
        repo.edit(
            existing.optLong("id"), existing.optLong("revision"), text, due, zone.getId(), r, days);
      AlarmScheduler.reconcile(context);
      main.post(
          () -> {
            if (!dismissed) {
              dialog.dismiss();
              saved.run();
            }
          });
    } catch (Exception e) {
      main.post(
          () -> {
            if (!dismissed) {
              dialog.getButton(-1).setEnabled(true);
              error.setText(
                  e.getMessage() == null ? "Проверьте дату или длительность" : e.getMessage());
            }
          });
    }
  }
}
