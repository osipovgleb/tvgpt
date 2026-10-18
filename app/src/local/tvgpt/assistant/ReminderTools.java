package local.tvgpt.assistant;

import android.content.Context;
import android.os.SystemClock;
import java.time.*;
import local.tvgpt.assistant.data.ReminderRepository;
import local.tvgpt.assistant.reminders.*;
import org.json.*;

final class ReminderTools {
  static ZonedDateTime currentTime(Context c) {
    return ZonedDateTime.now(new local.tvgpt.assistant.settings.GeneralPreferences(c).zone());
  }

  static Object execute(Context c, String name, JSONObject a) throws Exception {
    return execute(c, name, a, CancellationToken.NONE);
  }

  static Object execute(Context c, String name, JSONObject a, CancellationToken token)
      throws Exception {
    token.check();
    if (!java.util.Arrays.asList(
            "reminder_list",
            "reminder_cancel",
            "reminder_create",
            "timer_create",
            "reminder_update",
            "reminder_delete",
            "timer_update")
        .contains(name)) throw new IllegalArgumentException("Неизвестное действие с напоминанием");
    ReminderRepository repo = new ReminderRepository(c);
    token.check();
    if (name.equals("reminder_list")) return repo.list().toString();
    if (name.equals("reminder_cancel")) {
      String outcome = repo.cancelOutcome(a.getLong("id"), token);
      AlarmScheduler.reconcile(c);
      if (outcome.equals("cancelled")) AppDataEvents.changed(c, AppDataEvents.REMINDERS);
      return new JSONObject().put("cancelled", outcome.equals("cancelled")).put("outcome", outcome);
    }
    if (name.equals("reminder_update")
        || name.equals("timer_update")
        || name.equals("reminder_delete")) return change(c, repo, name, a, token);
    String body = a.getString("body");
    String zone = new local.tvgpt.assistant.settings.GeneralPreferences(c).zone().getId();
    long due, elapsed = 0;
    String repeat = "NONE", action = "", kind = "REMINDER";
    int days = 0;
    if (name.equals("timer_create")) {
      long seconds = a.getLong("seconds");
      elapsed = SchedulePolicy.timer(seconds, SystemClock.elapsedRealtime());
      due = System.currentTimeMillis() + seconds * 1000;
      kind = "TIMER";
    } else {
      zone = a.getString("zone");
      due = SchedulePolicy.calendar(a.getString("local_datetime"), ZoneId.of(zone));
      repeat = a.getString("repeat");
      days = a.getInt("weekdays");
      action = a.optString("action");
      if (!action.isEmpty()) {
        action = ReminderActions.validate(action);
        kind = "ACTION";
      }
    }
    long id =
        repo.create(
            body, due, zone, kind, repeat, days, elapsed, AlarmScheduler.boot(c), action, token);
    AlarmScheduler.reconcile(c);
    AppDataEvents.changed(c, AppDataEvents.REMINDERS);
    return new JSONObject()
        .put("id", id)
        .put("due", Instant.ofEpochMilli(due).atZone(ZoneId.of(zone)).toString())
        .put("standby_policy", "Ждать включения телевизора")
        .put("exact_alarm", AlarmScheduler.exact(c));
  }

  private static JSONObject change(
      Context c, ReminderRepository repo, String name, JSONObject a, CancellationToken token)
      throws Exception {
    long id = a.getLong("id"), revision = a.getLong("revision");
    JSONObject before = repo.get(id);
    if (before == null || before.optLong("revision") != revision)
      return new JSONObject().put("status", "conflict").put("changed", false);
    if (name.equals("reminder_delete")) {
      boolean deleted = repo.deleteIfCurrent(id, revision, token);
      if (!deleted) return new JSONObject().put("status", "conflict").put("deleted", false);
    } else if (name.equals("timer_update")) {
      repo.editTimer(
          id,
          revision,
          a.getString("body"),
          a.getLong("seconds"),
          System.currentTimeMillis(),
          SystemClock.elapsedRealtime(),
          AlarmScheduler.boot(c),
          token);
    } else {
      if (before.optString("kind").equals("TIMER"))
        throw new IllegalArgumentException("Для изменения таймера используй timer_update");
      String zone = a.getString("zone");
      long due = SchedulePolicy.calendar(a.getString("local_datetime"), ZoneId.of(zone));
      repo.edit(
          id,
          revision,
          a.getString("body"),
          due,
          zone,
          a.getString("repeat"),
          a.getInt("weekdays"),
          token);
    }
    JSONObject after = repo.get(id);
    boolean deleted = name.equals("reminder_delete");
    boolean observed =
        deleted ? after == null : after != null && after.optLong("revision") == revision + 1;
    AppDataEvents.changed(c, AppDataEvents.REMINDERS);
    AlarmScheduler.reconcile(c);
    return new JSONObject()
        .put("id", id)
        .put("status", observed ? (deleted ? "deleted" : "updated") : "unconfirmed")
        .put(
            "confirmed_scope",
            observed ? (deleted ? "reminder_absent" : "reminder_revision_readback") : "")
        .put("reminder", after == null ? JSONObject.NULL : after);
  }

  static void schemas(JSONArray out) throws Exception {
    out.put(Agent.tool("reminder_list", "Прочитать напоминания и таймеры", new JSONObject()));
    mutationSchemas(out);
    out.put(
        Agent.tool(
            "reminder_cancel",
            "Отменить напоминание по явному запросу",
            new JSONObject().put("id", type("integer"))));
    out.put(
        Agent.tool(
            "timer_create",
            "Поставить таймер на указанное пользователем число секунд. В ожидании ТВ не"
                + " включается.",
            new JSONObject().put("body", type("string")).put("seconds", type("integer"))));
    out.put(
        Agent.tool(
            "reminder_create",
            "Создать только с ясными датой, временем и часовым поясом. Если времени нет или оно"
                + " неоднозначно — уточни. local_datetime ISO yyyy-MM-ddTHH:mm; zone IANA. repeat"
                + " NONE/DAILY/WEEKLY, weekdays битовая маска Пн=1,Вт=2,...Вс=64; 0 без недельного"
                + " повтора. action пустая строка для напоминания, иначе JSON"
                + " {tool:open_app/volume,arguments:{...}} только по явному запросу пользователя."
                + " После сохранения сообщи точную дату и время.",
            new JSONObject()
                .put("body", type("string"))
                .put("local_datetime", type("string"))
                .put("zone", type("string"))
                .put("repeat", type("string"))
                .put("weekdays", type("integer"))
                .put("action", type("string"))));
  }

  private static void mutationSchemas(JSONArray out) throws Exception {
    out.put(
        Agent.tool(
            "reminder_delete",
            "Удалить запись напоминания или таймера только по явной просьбе. Сначала reminder_list:"
                + " нужны id и revision. Отмена и удаление — разные действия.",
            new JSONObject().put("id", type("integer")).put("revision", type("integer"))));
    out.put(
        Agent.tool(
            "reminder_update",
            "Изменить существующее напоминание по просьбе пользователя, сохранив его id. Сначала"
                + " reminder_list: используй revision и сохрани не запрошенные к изменению поля."
                + " local_datetime ISO, zone IANA, repeat NONE/DAILY/WEEKLY, weekdays Пн=1..Вс=64."
                + " Действие ACTION сохраняется. Для TIMER используй timer_update. Не отменяй и не"
                + " создавай заново.",
            new JSONObject()
                .put("id", type("integer"))
                .put("revision", type("integer"))
                .put("body", type("string"))
                .put("local_datetime", type("string"))
                .put("zone", type("string"))
                .put("repeat", type("string"))
                .put("weekdays", type("integer"))));
    out.put(
        Agent.tool(
            "timer_update",
            "Изменить таймер по явной просьбе. Сначала reminder_list для id/revision. seconds>0"
                + " задаёт новый отсчёт от текущего момента; seconds=0 меняет только текст,"
                + " сохраняя срок. Таймер остаётся TIMER, без календарного преобразования. Не"
                + " создавай второй таймер.",
            new JSONObject()
                .put("id", type("integer"))
                .put("revision", type("integer"))
                .put("body", type("string"))
                .put("seconds", type("integer"))));
  }

  private static JSONObject type(String t) throws Exception {
    return new JSONObject().put("type", t);
  }
}
