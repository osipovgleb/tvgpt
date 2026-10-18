package local.tvgpt.assistant.ui;

import android.content.Context;
import android.view.*;
import android.widget.*;
import local.tvgpt.assistant.SettingsGlyph;

/** The sheet's ListView owns selection and OK handling; decorative children never take focus. */
final class IconListAdapter extends BaseAdapter {
  private final Context context;
  private final String[] labels;
  private final int[] icons;

  IconListAdapter(Context context, String[] labels, int[] icons) {
    this.context = context;
    this.labels = labels.clone();
    this.icons = icons.clone();
  }

  public int getCount() {
    return labels.length;
  }

  public String getItem(int position) {
    return labels[position];
  }

  public long getItemId(int position) {
    return position;
  }

  public View getView(int position, View recycled, ViewGroup parent) {
    LinearLayout row = new LinearLayout(context);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(
        Screens.dp(context, 16),
        Screens.dp(context, 12),
        Screens.dp(context, 16),
        Screens.dp(context, 12));
    row.setMinimumHeight(Screens.dp(context, 54));
    row.setContentDescription(labels[position]);
    row.addView(
        new SettingsGlyph(context, icons[position]),
        new LinearLayout.LayoutParams(Screens.dp(context, 24), Screens.dp(context, 24)));
    LinearLayout.LayoutParams text = new LinearLayout.LayoutParams(0, -2, 1);
    text.leftMargin = Screens.dp(context, 14);
    row.addView(LibraryCard.label(context, labels[position], 16, 0xffeeeeee, 2), text);
    return row;
  }
}
