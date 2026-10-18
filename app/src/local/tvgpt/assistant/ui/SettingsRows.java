package local.tvgpt.assistant.ui;

import android.content.Context;
import android.view.*;
import android.widget.*;
import local.tvgpt.assistant.SettingsGlyph;

/** The original settings groups, shared without restyling between Appearance and Settings. */
public final class SettingsRows {
  private final Context context;

  public SettingsRows(Context context) {
    this.context = context;
  }

  private int dp(float value) {
    return Screens.dp(context, value);
  }

  private TextView plain(String text, int size, int color) {
    TextView v = new TextView(context);
    v.setText(text);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setIncludeFontPadding(false);
    return v;
  }

  public LinearLayout group(LinearLayout box, String label) {
    if (!label.isEmpty()) {
      TextView section = plain(label, 13, 0xffa1a1a6);
      section.setPadding(dp(14), dp(22), 0, dp(9));
      box.addView(section);
    }
    LinearLayout group = new LinearLayout(context);
    group.setOrientation(1);
    android.graphics.drawable.GradientDrawable bg =
        new android.graphics.drawable.GradientDrawable();
    bg.setColor(0xff1c1c1e);
    bg.setCornerRadius(dp(15));
    group.setBackground(bg);
    group.setClipToOutline(true);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    if (label.isEmpty()) p.topMargin = dp(22);
    box.addView(group, p);
    return group;
  }

  public LinearLayout row(
      LinearLayout group, int kind, String title, String subtitle, Runnable action) {
    if (group.getChildCount() > 0) {
      View line = new View(context);
      line.setBackgroundColor(0xff303034);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(.5f));
      lp.leftMargin = dp(54);
      group.addView(line, lp);
    }
    LinearLayout row = new LinearLayout(context);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(16), dp(14), dp(16), dp(14));
    row.setMinimumHeight(dp(62));
    SettingsGlyph icon = new SettingsGlyph(context, kind);
    LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(24), dp(24));
    ip.rightMargin = dp(15);
    row.addView(icon, ip);
    LinearLayout labels = new LinearLayout(context);
    labels.setOrientation(1);
    row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    TextView label = plain(title, 16, 0xfff1f1f3);
    labels.addView(label);
    TextView detail = plain(subtitle, 12, 0xff96969e);
    if (subtitle.isEmpty()) detail.setVisibility(View.GONE);
    else detail.setPadding(0, dp(5), 0, 0);
    labels.addView(detail);
    row.setTag(detail);
    if (action != null) {
      SettingsGlyph arrow = new SettingsGlyph(context, SettingsGlyph.CHEVRON);
      row.addView(arrow, new LinearLayout.LayoutParams(dp(18), dp(18)));
      row.setFocusable(true);
      row.setOnClickListener(v -> action.run());
      android.graphics.drawable.StateListDrawable bg =
          new android.graphics.drawable.StateListDrawable();
      android.graphics.drawable.ColorDrawable focused =
          new android.graphics.drawable.ColorDrawable(0xff3b3b3f);
      bg.addState(new int[] {android.R.attr.state_focused}, focused);
      bg.addState(new int[] {android.R.attr.state_pressed}, focused);
      bg.addState(
          new int[] {},
          new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
      row.setBackground(bg);
    }
    group.addView(row, new LinearLayout.LayoutParams(-1, -2));
    return row;
  }
}
