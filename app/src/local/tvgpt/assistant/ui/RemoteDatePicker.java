package local.tvgpt.assistant.ui;

import android.content.Context;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.*;
import java.util.Locale;
import java.util.function.Consumer;
import local.tvgpt.assistant.reminders.RemoteDateTime;

/** Framework buttons form a calendar with explicit TV focus paths, not touch-only month paging. */
final class RemoteDatePicker {
  private final Context context;
  private final Consumer<LocalDate> chosen;
  private final Button[] cells = new Button[42];
  private final ArrowRow year, month;
  private LocalDate selected;

  RemoteDatePicker(Context c, LocalDate initial, Consumer<LocalDate> chosen) {
    context = c;
    selected = initial;
    this.chosen = chosen;
    year = new ArrowRow(c, "Год", () -> Integer.toString(selected.getYear()), d -> change(true, d));
    month =
        new ArrowRow(
            c,
            "Месяц",
            () ->
                selected.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()),
            d -> change(false, d));
  }

  LinearLayout view() {
    LinearLayout box = new LinearLayout(context);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(0, 0, 0, 0);
    box.addView(year);
    box.addView(month);
    year.setNextFocusDownId(month.getId());
    month.setNextFocusUpId(year.getId());
    GridLayout grid = new GridLayout(context);
    grid.setColumnCount(7);
    grid.setRowCount(7);
    String[] weekdays = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
    for (int i = 0; i < weekdays.length; i++) {
      TextView label = Screens.text(context, weekdays[i], 12);
      label.setPadding(0, 0, 0, 0);
      label.setSingleLine(true);
      label.setGravity(Gravity.CENTER);
      grid.addView(label, cellSize(0, i, 24));
    }
    for (int i = 0; i < cells.length; i++) {
      Button cell = new Button(context);
      cell.setId(View.generateViewId());
      cell.setTextSize(15);
      cell.setPadding(0, 0, 0, 0);
      cell.setMinHeight(0);
      cell.setMinWidth(0);
      cell.setStateListAnimator(null);
      cell.setOnClickListener(v -> choose((LocalDate) v.getTag()));
      cell.setOnKeyListener((v, key, event) -> move((LocalDate) v.getTag(), key, event));
      cells[i] = cell;
      grid.addView(cell, cellSize(1 + i / 7, i % 7, 34));
    }
    box.addView(grid);
    render();
    return box;
  }

  private GridLayout.LayoutParams cellSize(int row, int column, int height) {
    GridLayout.LayoutParams p = new GridLayout.LayoutParams();
    p.width = 0;
    p.height = Screens.dp(context, height);
    p.rowSpec = GridLayout.spec(row);
    p.columnSpec = GridLayout.spec(column, 1f);
    return p;
  }

  private void change(boolean years, int delta) {
    LocalDate next =
        years ? RemoteDateTime.year(selected, delta) : RemoteDateTime.month(selected, delta);
    if (next.getYear() < 1 || next.getYear() > 9999) return;
    selected = next;
    render();
    chosen.accept(selected);
  }

  private void choose(LocalDate date) {
    if (date == null) return;
    chosen.accept(date);
    selected = date;
    render();
  }

  private void render() {
    year.refresh();
    month.refresh();
    int start = RemoteDateTime.firstCell(selected), days = selected.lengthOfMonth();
    for (int i = 0; i < cells.length; i++) {
      int day = i - start + 1;
      boolean valid = day > 0 && day <= days;
      Button cell = cells[i];
      cell.setEnabled(valid);
      cell.setFocusable(valid);
      cell.setText(valid ? Integer.toString(day) : "");
      cell.setTag(valid ? selected.withDayOfMonth(day) : null);
      cell.setContentDescription(
          valid
              ? selected
                  .withDayOfMonth(day)
                  .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
              : "");
      android.graphics.drawable.StateListDrawable bg =
          new android.graphics.drawable.StateListDrawable();
      bg.addState(
          new int[] {android.R.attr.state_focused}, Screens.rounded(context, 0xff5a5a65, 8));
      bg.addState(
          new int[] {},
          Screens.rounded(
              context, valid && day == selected.getDayOfMonth() ? 0xff363640 : 0xff1c1c1e, 8));
      cell.setBackground(bg);
    }
    month.setNextFocusDownId(cells[start + selected.getDayOfMonth() - 1].getId());
  }

  private boolean move(LocalDate date, int key, KeyEvent event) {
    if (date == null) return false;
    int delta;
    switch (key) {
      case KeyEvent.KEYCODE_DPAD_LEFT:
        delta = -1;
        break;
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        delta = 1;
        break;
      case KeyEvent.KEYCODE_DPAD_UP:
        if (date.getDayOfMonth() <= 7) {
          if (event.getAction() == KeyEvent.ACTION_DOWN) month.requestFocus();
          return true;
        }
        delta = -7;
        break;
      case KeyEvent.KEYCODE_DPAD_DOWN:
        if (date.getDayOfMonth() + 7 > date.lengthOfMonth()) return false;
        delta = 7;
        break;
      default:
        return false;
    }
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      selected = date.plusDays(delta);
      render();
      chosen.accept(selected);
      cells[RemoteDateTime.firstCell(selected) + selected.getDayOfMonth() - 1].requestFocus();
    }
    return true;
  }
}
