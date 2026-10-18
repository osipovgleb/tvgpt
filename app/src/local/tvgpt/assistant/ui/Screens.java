package local.tvgpt.assistant.ui;

import android.content.Context;
import android.graphics.drawable.*;
import android.view.View;
import android.widget.*;

public final class Screens {
  private Screens() {}

  public static int dp(Context c, float n) {
    return (int) (n * c.getResources().getDisplayMetrics().density + .5f);
  }

  public static TextView text(Context c, String s, int size) {
    TextView t = new TextView(c);
    t.setText(s);
    t.setTextColor(0xffeeeeee);
    t.setTextSize(size);
    t.setIncludeFontPadding(false);
    t.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
    t.setPadding(0, dp(c, 8), 0, dp(c, 8));
    return t;
  }

  public static LinearLayout column(Context c, String title) {
    LinearLayout b = new LinearLayout(c);
    b.setOrientation(1);
    b.setPadding(dp(c, 30), dp(c, 20), dp(c, 30), dp(c, 24));
    return b;
  }

  public static View scroll(Context c, View child) {
    ScrollView s = new ScrollView(c);
    s.setFillViewport(true);
    s.addView(child);
    return s;
  }

  public static Button button(Context c, String text, Runnable action) {
    Button b = new Button(c);
    b.setText(text);
    b.setTextColor(0xfff1f1f3);
    b.setAllCaps(false);
    b.setTextSize(16);
    b.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
    b.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
    b.setIncludeFontPadding(false);
    b.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
    b.setStateListAnimator(null);
    b.setMinHeight(dp(c, 62));
    StateListDrawable bg = new StateListDrawable();
    bg.addState(new int[] {android.R.attr.state_focused}, rounded(c, 0xff3b3b3f, 15));
    bg.addState(new int[] {android.R.attr.state_pressed}, rounded(c, 0xff3b3b3f, 15));
    bg.addState(new int[] {}, rounded(c, 0xff1c1c1e, 15));
    b.setBackground(bg);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(c, 8);
    b.setLayoutParams(lp);
    b.setOnClickListener(v -> action.run());
    return b;
  }

  public static View action(Context c, int icon, String title, Runnable action) {
    LinearLayout row =
        new LinearLayout(c) {
          @Override
          public CharSequence getAccessibilityClassName() {
            return Button.class.getName();
          }
        };
    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
    row.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
    row.setMinimumHeight(dp(c, 54));
    row.setFocusable(true);
    row.setClickable(true);
    row.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    row.setContentDescription(title);
    row.setBackground(LibraryCard.background(c));
    row.addView(
        new local.tvgpt.assistant.SettingsGlyph(c, icon),
        new LinearLayout.LayoutParams(dp(c, 24), dp(c, 24)));
    LinearLayout.LayoutParams label = new LinearLayout.LayoutParams(0, -2, 1);
    label.leftMargin = dp(c, 12);
    row.addView(LibraryCard.label(c, title, 15, 0xffeeeeee, 2), label);
    LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, -2);
    outer.topMargin = dp(c, 8);
    row.setLayoutParams(outer);
    row.setOnClickListener(v -> action.run());
    return row;
  }

  public static android.graphics.drawable.GradientDrawable rounded(
      Context c, int colour, int radius) {
    android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
    d.setColor(colour);
    d.setCornerRadius(dp(c, radius));
    return d;
  }

  public static View empty(Context c, String title, String message) {
    LinearLayout b = column(c, title);
    b.addView(text(c, message, 16));
    return b;
  }
}
