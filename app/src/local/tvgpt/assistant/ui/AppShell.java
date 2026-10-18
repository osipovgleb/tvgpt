package local.tvgpt.assistant.ui;

import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.view.*;
import android.widget.*;
import java.util.function.Consumer;
import local.tvgpt.assistant.AppNavigation.Route;
import local.tvgpt.assistant.SettingsGlyph;

public final class AppShell extends LinearLayout {
  private final FrameLayout content;
  private final LinearLayout[] rows = new LinearLayout[6];
  private final TextView[] labels = new TextView[6];
  private final Route[] routes = {
    Route.CONVERSATIONS,
    Route.KNOWLEDGE,
    Route.REMINDERS,
    Route.DIGESTS,
    Route.APPEARANCE,
    Route.ACCESS
  };

  public AppShell(Context c, Consumer<Route> navigate) {
    super(c);
    setOrientation(HORIZONTAL);
    setBackgroundColor(0xff0f0f0f);
    LinearLayout menu = new LinearLayout(c);
    menu.setOrientation(VERTICAL);
    menu.setBackgroundColor(0xff1a1a1d);
    menu.setPadding(Screens.dp(c, 16), Screens.dp(c, 20), Screens.dp(c, 16), Screens.dp(c, 20));
    ImageView logo = new ImageView(c);
    logo.setImageResource(
        getResources().getIdentifier("sidebar_logo", "drawable", c.getPackageName()));
    logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
    logo.setContentDescription("TV GPT");
    logo.setFocusable(false);
    LayoutParams logoParams = new LayoutParams(Screens.dp(c, 72), Screens.dp(c, 50));
    logoParams.gravity = Gravity.CENTER_HORIZONTAL;
    logoParams.bottomMargin = Screens.dp(c, 12);
    menu.addView(logo, logoParams);
    String[] names = {
      "Разговоры", "Общие знания", "Напоминания", "Дайджесты", "Настройки ассистента", "Настройки"
    };
    int[] icons = {
      SettingsGlyph.CHAT,
      SettingsGlyph.BOOK,
      SettingsGlyph.BELL,
      SettingsGlyph.NEWS,
      SettingsGlyph.PALETTE,
      SettingsGlyph.SHIELD
    };
    for (int i = 0; i < routes.length; i++) {
      final Route route = routes[i];
      rows[i] = new LinearLayout(c);
      rows[i].setGravity(Gravity.CENTER_VERTICAL);
      rows[i].setPadding(Screens.dp(c, 10), 0, Screens.dp(c, 8), 0);
      rows[i].setFocusable(true);
      rows[i].setOnClickListener(v -> navigate.accept(route));
      StateListDrawable bg = new StateListDrawable();
      bg.addState(new int[] {android.R.attr.state_focused}, focusedBackground(c));
      bg.addState(new int[] {android.R.attr.state_activated}, Screens.rounded(c, 0xff29292f, 12));
      bg.addState(new int[] {}, Screens.rounded(c, 0xff1a1a1d, 12));
      rows[i].setBackground(bg);
      rows[i].addView(
          new SettingsGlyph(c, icons[i]), new LayoutParams(Screens.dp(c, 18), Screens.dp(c, 18)));
      labels[i] = Screens.text(c, names[i], 14);
      labels[i].setPadding(Screens.dp(c, 10), 0, 0, 0);
      rows[i].addView(labels[i], new LayoutParams(0, -2, 1));
      LayoutParams p = new LayoutParams(-1, Screens.dp(c, 42));
      p.topMargin = Screens.dp(c, 3);
      menu.addView(rows[i], p);
    }
    addView(menu, new LayoutParams(Screens.dp(c, 180), -1));
    content = new FrameLayout(c);
    addView(content, new LayoutParams(0, -1, 1));
  }

  private static android.graphics.drawable.GradientDrawable focusedBackground(Context c) {
    android.graphics.drawable.GradientDrawable bg = Screens.rounded(c, 0xff36363f, 12);
    bg.setStroke(Screens.dp(c, 1), 0xff777784);
    return bg;
  }

  public boolean focusMenu() {
    if (!content.hasFocus()) return false;
    for (LinearLayout row : rows) if (row.isActivated()) return row.requestFocus();
    return false;
  }

  public void show(Route route, View view) {
    content.removeAllViews();
    int width =
        Math.min(
            Screens.dp(getContext(), 620),
            getResources().getDisplayMetrics().widthPixels - Screens.dp(getContext(), 200));
    content.addView(view, new FrameLayout.LayoutParams(width, -1, Gravity.CENTER_HORIZONTAL));
    Route selected = route;
    for (int i = 0; i < rows.length; i++) {
      rows[i].setActivated(routes[i] == selected);
      labels[i].setTextColor(routes[i] == selected ? 0xfff5f5f5 : 0xffa1a1a6);
      if (routes[i] == selected && route == selected) rows[i].requestFocus();
    }
  }
}
