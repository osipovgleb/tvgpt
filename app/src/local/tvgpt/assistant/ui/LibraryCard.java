package local.tvgpt.assistant.ui;

import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.*;
import local.tvgpt.assistant.SettingsGlyph;

/** One remote-focus target containing its title, preview and metadata. */
public final class LibraryCard extends LinearLayout {
  public LibraryCard(
      Context c, int icon, String title, String preview, String metadata, Runnable action) {
    super(c);
    setOrientation(VERTICAL);
    setPadding(Screens.dp(c, 16), Screens.dp(c, 16), Screens.dp(c, 16), Screens.dp(c, 16));
    setFocusable(true);
    setClickable(true);
    setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
    setContentDescription(title + ". " + preview + ". " + metadata);
    setBackground(background(c));
    LinearLayout heading = new LinearLayout(c);
    heading.setGravity(Gravity.CENTER_VERTICAL);
    heading.addView(
        new SettingsGlyph(c, icon), new LayoutParams(Screens.dp(c, 24), Screens.dp(c, 24)));
    TextView name = label(c, title, 17, 0xffeeeeee, 2);
    LayoutParams nameLayout = new LayoutParams(0, -2, 1);
    nameLayout.leftMargin = Screens.dp(c, 12);
    nameLayout.rightMargin = Screens.dp(c, 8);
    heading.addView(name, nameLayout);
    heading.addView(
        new SettingsGlyph(c, SettingsGlyph.CHEVRON),
        new LayoutParams(Screens.dp(c, 18), Screens.dp(c, 18)));
    addView(heading, new LayoutParams(-1, -2));
    if (!preview.trim().isEmpty()) {
      TextView detail = label(c, preview, 14, 0xffb6b6bd, 3);
      LayoutParams detailLayout = new LayoutParams(-1, -2);
      detailLayout.topMargin = Screens.dp(c, 12);
      addView(detail, detailLayout);
    }
    if (!metadata.trim().isEmpty()) {
      TextView footer = label(c, metadata, 12, 0xff909099, 2);
      LayoutParams footerLayout = new LayoutParams(-1, -2);
      footerLayout.topMargin = Screens.dp(c, 12);
      addView(footer, footerLayout);
    }
    LayoutParams outer = new LayoutParams(-1, -2);
    outer.topMargin = Screens.dp(c, 12);
    setLayoutParams(outer);
    setOnClickListener(v -> action.run());
  }

  public LibraryCard withKey(String key) {
    setTag(key);
    return this;
  }

  static TextView label(Context c, String text, int size, int colour, int lines) {
    TextView label = Screens.text(c, text, size);
    label.setPadding(0, 0, 0, 0);
    label.setTextColor(colour);
    label.setMaxLines(lines);
    label.setEllipsize(TextUtils.TruncateAt.END);
    label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    return label;
  }

  static StateListDrawable background(Context c) {
    StateListDrawable states = new StateListDrawable();
    android.graphics.drawable.GradientDrawable focused = Screens.rounded(c, 0xff303036, 16);
    focused.setStroke(Screens.dp(c, 1), 0xff8c8c98);
    states.addState(new int[] {android.R.attr.state_focused}, focused);
    states.addState(new int[] {android.R.attr.state_pressed}, Screens.rounded(c, 0xff36363f, 16));
    states.addState(new int[] {}, Screens.rounded(c, 0xff1c1c1e, 16));
    return states;
  }

  @Override
  public CharSequence getAccessibilityClassName() {
    return Button.class.getName();
  }
}
