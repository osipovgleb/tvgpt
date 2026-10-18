package local.tvgpt.assistant.ui;

import android.content.Context;
import android.view.*;
import android.widget.*;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** D-pad up/down selects a field; left/right changes it. No touch spinner or keyboard. */
final class ArrowRow extends LinearLayout {
  private final TextView value;
  private final Supplier<String> read;
  private final String title;

  ArrowRow(Context c, String title, Supplier<String> read, IntConsumer change) {
    super(c);
    this.read = read;
    this.title = title;
    setId(View.generateViewId());
    setGravity(Gravity.CENTER_VERTICAL);
    setFocusable(true);
    setClickable(true);
    setPadding(Screens.dp(c, 14), 0, Screens.dp(c, 14), 0);
    setBackgroundColor(android.graphics.Color.TRANSPARENT);
    TextView heading = Screens.text(c, title, 15);
    addView(heading, new LayoutParams(0, -2, 1));
    value = Screens.text(c, "", 16);
    addView(value);
    setOnFocusChangeListener(
        (v, focus) -> {
          setBackground(
              focus
                  ? Screens.rounded(c, 0xff3b3b3f, 8)
                  : new android.graphics.drawable.ColorDrawable(
                      android.graphics.Color.TRANSPARENT));
        });
    LayoutParams size = new LayoutParams(-1, Screens.dp(c, 40));
    size.topMargin = Screens.dp(c, 6);
    setLayoutParams(size);
    setOnClickListener(
        v -> {
          change.accept(1);
          refresh();
        });
    setOnKeyListener(
        (v, key, event) -> {
          if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
          if (event.getAction() == KeyEvent.ACTION_DOWN) {
            change.accept(key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            refresh();
          }
          return true;
        });
    refresh();
  }

  void refresh() {
    value.setText("‹  " + read.get() + "  ›");
    setContentDescription(title + ": " + read.get());
  }
}
