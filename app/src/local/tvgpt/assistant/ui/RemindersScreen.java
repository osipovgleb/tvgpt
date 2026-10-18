package local.tvgpt.assistant.ui;

import android.app.*;
import android.content.Context;
import android.widget.*;
import java.time.*;
import local.tvgpt.assistant.SettingsGlyph;
import local.tvgpt.assistant.data.ReminderRepository;
import local.tvgpt.assistant.reminders.*;
import org.json.*;

public final class RemindersScreen extends AsyncScreen {
  public RemindersScreen(Context c) {
    super(c, "Напоминания");
    observeData(local.tvgpt.assistant.AppDataEvents.REMINDERS, this::refresh);
    refresh();
  }

  private void refresh() {
    load(
        () -> new ReminderRepository(getContext()).list(),
        items -> {
          body.removeAllViews();
          body.addView(
              Screens.action(
                  getContext(), SettingsGlyph.PLUS, "Добавить напоминание", () -> create(false)));
          body.addView(
              Screens.action(
                  getContext(), SettingsGlyph.TIMER, "Поставить таймер", () -> create(true)));
          for (int i = 0; i < items.length(); i++) {
            JSONObject n = items.optJSONObject(i);
            String date =
                Instant.ofEpochMilli(n.optLong("due"))
                    .atZone(ZoneId.of(n.optString("zone")))
                    .format(
                        java.time.format.DateTimeFormatter.ofLocalizedDateTime(
                            java.time.format.FormatStyle.SHORT));
            body.addView(
                new LibraryCard(
                        getContext(),
                        SettingsGlyph.BELL,
                        n.optString("body"),
                        date,
                        statusLabel(n.optString("status")),
                        () ->
                            new RightSheet.Builder(getContext())
                                .setTitle(n.optString("body"))
                                .setIconItems(
                                    new String[] {"Изменить", "Отменить", "Удалить"},
                                    new int[] {
                                      SettingsGlyph.EDIT, SettingsGlyph.STOP, SettingsGlyph.DELETE
                                    },
                                    (d, which) -> {
                                      if (which == 0) {
                                        new ReminderEditor(getContext(), false, n, this::refresh)
                                            .show();
                                        return;
                                      }
                                      if (which == 2) {
                                        new RightSheet.Builder(getContext())
                                            .setMessage("Удалить напоминание?")
                                            .setPositiveButton(
                                                "Удалить",
                                                (confirm, button) ->
                                                    load(
                                                        () -> {
                                                          new ReminderRepository(getContext())
                                                              .delete(n.optLong("id"));
                                                          AlarmScheduler.reconcile(getContext());
                                                          return true;
                                                        },
                                                        ok -> refresh()))
                                            .setNegativeButton("Отмена", null)
                                            .show();
                                        return;
                                      }
                                      load(
                                          () -> {
                                            ReminderRepository repo =
                                                new ReminderRepository(getContext());
                                            if (which == 1 && !repo.cancel(n.optLong("id")))
                                              throw new IllegalStateException(
                                                  "Напоминание уже выполняется или завершено");

                                            AlarmScheduler.reconcile(getContext());
                                            return true;
                                          },
                                          ok -> refresh());
                                    })
                                .show())
                    .withKey("reminder:" + n.optLong("id")));
          }
        });
  }

  private String statusLabel(String state) {
    switch (state) {
      case "scheduled":
        return "Запланировано";
      case "pending":
        return "Ждёт включения ТВ";
      case "delivering":
        return "Выполняется";
      case "notified":
        return "Уведомление отправлено";
      case "completed":
        return "Выполнено";
      case "unconfirmed":
        return "Результат не подтверждён";
      case "failed":
        return "Не удалось выполнить";
      case "cancelled":
        return "Отменено";
      default:
        return "";
    }
  }

  private void create(boolean timer) {
    new ReminderEditor(getContext(), timer, null, this::refresh).show();
  }
}
