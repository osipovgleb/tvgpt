package local.tvgpt.assistant.digests;

import java.time.*;
import java.time.zone.ZoneOffsetTransition;
import org.json.*;

/** Pure wall-clock policy: DST gaps advance to the first valid local instant. */
public final class DigestSchedulePolicy {
  private DigestSchedulePolicy() {}

  public static long next(JSONObject s, long after) throws Exception {
    ZoneId zone = ZoneId.of(s.optString("zone", "UTC"));
    String repeat = s.optString("repeat", "daily");
    if (repeat.equals("interval")) {
      long minutes = s.optLong("interval_minutes", 60);
      if (minutes < 60 || minutes > 525600)
        throw new IllegalArgumentException("Интервал от часа до года");
      long span = minutes * 60000;
      return Math.addExact(Math.floorDiv(after, span) * span, span);
    }
    if (!repeat.equals("daily") && !repeat.equals("weekly"))
      throw new IllegalArgumentException("Неизвестное расписание");
    int hour = s.optInt("hour", 9), minute = s.optInt("minute", 0);
    LocalTime time = LocalTime.of(hour, minute);
    int days = s.optInt("weekdays", 127);
    if (repeat.equals("weekly") && (days <= 0 || days > 127))
      throw new IllegalArgumentException("Выберите дни недели");
    LocalDate date = Instant.ofEpochMilli(after).atZone(zone).toLocalDate();
    for (int i = 0; i < 9; i++, date = date.plusDays(1)) {
      if (repeat.equals("weekly") && (days & (1 << (date.getDayOfWeek().getValue() - 1))) == 0)
        continue;
      LocalDateTime local = LocalDateTime.of(date, time);
      ZoneOffsetTransition transition = zone.getRules().getTransition(local);
      ZonedDateTime due =
          transition != null && transition.isGap()
              ? transition.getDateTimeAfter().atZone(zone)
              : local.atZone(zone).withEarlierOffsetAtOverlap();
      if (due.toInstant().toEpochMilli() > after) return due.toInstant().toEpochMilli();
    }
    throw new IllegalArgumentException("Нет следующего срока");
  }

  public static long slot(JSONObject s, long now) throws Exception {
    if (s.optString("repeat", "daily").equals("interval")) {
      long next = next(s, now);
      return next - s.optLong("interval_minutes", 60) * 60000;
    }
    long candidate = next(s, now - 9 * 86400000L), last = -1;
    while (candidate <= now) {
      last = candidate;
      candidate = next(s, candidate);
    }
    return last;
  }
}
