package local.tvgpt.assistant.ui;

import android.content.Context;
import android.widget.LinearLayout;
import java.time.LocalTime;
import java.util.Locale;
import java.util.function.Consumer;
import local.tvgpt.assistant.reminders.RemoteDateTime;

final class RemoteTimePicker {
  private RemoteTimePicker() {}

  static LinearLayout view(Context c, LocalTime initial, Consumer<LocalTime> chosen) {
    LocalTime[] value = {initial.withSecond(0).withNano(0)};
    LinearLayout box = new LinearLayout(c);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(0, 0, 0, 0);
    ArrowRow hour =
        new ArrowRow(
            c,
            "Часы",
            () -> String.format(Locale.getDefault(), "%02d", value[0].getHour()),
            d -> {
              value[0] = RemoteDateTime.hour(value[0], d);
              chosen.accept(value[0]);
            });
    ArrowRow minute =
        new ArrowRow(
            c,
            "Минуты",
            () -> String.format(Locale.getDefault(), "%02d", value[0].getMinute()),
            d -> {
              value[0] = RemoteDateTime.minute(value[0], d);
              chosen.accept(value[0]);
            });
    box.addView(hour);
    box.addView(minute);
    hour.setNextFocusDownId(minute.getId());
    minute.setNextFocusUpId(hour.getId());
    return box;
  }
}
