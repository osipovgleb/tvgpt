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
    ReminderRepository repo = new ReminderRepository(c);
    token.check();
    if (name.equals("reminder_list")) return repo.list().toString();
    if (name.equals("reminder_cancel")) {
      String outcome = repo.cancelOutcome(a.getLong("id"), token);
      AlarmScheduler.reconcile(c);
      return new JSONObject().put("cancelled", outcome.equals("cancelled")).put("outcome", outcome);
    }
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
    return new JSONObject()
        .put("id", id)
        .put("due", Instant.ofEpochMilli(due).atZone(ZoneId.of(zone)).toString())
        .put("standby_policy", "Ждать включения телевизора")
        .put("exact_alarm", AlarmScheduler.exact(c));
  }

  static void schemas(JSONArray out) throws Exception {
    out.put(Agent.tool("reminder_list", "Прочитать напоминания и таймеры", new JSONObject()));
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

  private static JSONObject type(String t) throws Exception {
    return new JSONObject().put("type", t);
  }
}
