package local.tvgpt.assistant.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.text.*;
import android.widget.*;
import java.time.*;
import java.util.*;
import local.tvgpt.assistant.settings.GeneralPreferences;

public final class TimezoneSetting {
  private TimezoneSetting() {}

  public static void show(Context context, Runnable changed) {
    chooseZone(context, new GeneralPreferences(context), changed);
  }

  public static String label(Context context) {
    return label(new GeneralPreferences(context).zone());
  }

  private static String label(ZoneId zone) {
    ZoneOffset offset = zone.getRules().getOffset(Instant.now());
    return zone.getId()
        + " (UTC"
        + (offset.equals(ZoneOffset.UTC) ? "+00:00" : offset.getId())
        + ")";
  }

  private static void chooseZone(Context c, GeneralPreferences prefs, Runnable changed) {
    List<String> zones = new ArrayList<>(ZoneId.getAvailableZoneIds());
    Collections.sort(zones);
    zones.remove(prefs.zone().getId());
    zones.add(0, prefs.zone().getId());
    LinearLayout box = new LinearLayout(c);
    box.setOrientation(LinearLayout.VERTICAL);
    EditText search = new EditText(c);
    search.setSingleLine(true);
    search.setHint("Поиск: Moscow, Berlin, Tokyo…");
    box.addView(search);
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(c, android.R.layout.simple_list_item_1, new ArrayList<>(zones));
    ListView list = new ListView(c);
    list.setAdapter(adapter);
    box.addView(list, new LinearLayout.LayoutParams(-1, Screens.dp(c, 280)));
    AlertDialog dialog =
        new RightSheet.Builder(c)
            .setTitle("Часовой пояс")
            .setView(box)
            .setNegativeButton("Отмена", null)
            .create();
    list.setOnItemClickListener(
        (v, row, position, id) -> {
          prefs.zone(adapter.getItem(position));
          changed.run();
          dialog.dismiss();
        });
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            adapter.getFilter().filter(s);
          }

          public void afterTextChanged(Editable e) {}
        });
    RightSheet.show(dialog);
    list.requestFocus();
  }
}
