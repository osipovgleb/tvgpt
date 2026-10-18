package local.tvgpt.assistant.reminders;

import java.time.*;

public final class SchedulePolicy {
  private SchedulePolicy() {}

  public static long calendar(String local, ZoneId zone) {
    LocalDateTime value = LocalDateTime.parse(local);
    if (zone.getRules().getValidOffsets(value).size() != 1)
      throw new IllegalArgumentException("Уточните время: переход часового пояса");
    return value.atZone(zone).toInstant().toEpochMilli();
  }

  public static long timer(long seconds, long nowElapsed) {
    if (seconds <= 0 || seconds > 30L * 86400)
      throw new IllegalArgumentException("Таймер: от 1 секунды до 30 дней");
    return Math.addExact(nowElapsed, Math.multiplyExact(seconds, 1000));
  }

  public static long timerWallDue(
      long wallNow, long elapsedNow, long elapsedDue, boolean sameBoot, long fallback) {
    return sameBoot ? Math.addExact(wallNow, Math.max(0, elapsedDue - elapsedNow)) : fallback;
  }

  public static boolean deliver(boolean interactive) {
    return interactive;
  }

  public static long next(long due, String repeat, int days, ZoneId zone, long after) {
    if ("NONE".equals(repeat)) return 0;
    if (!"DAILY".equals(repeat) && !"WEEKLY".equals(repeat))
      throw new IllegalArgumentException("Повтор: NONE, DAILY или WEEKLY");
    if ("WEEKLY".equals(repeat) && (days < 1 || days > 127))
      throw new IllegalArgumentException("Укажите дни недели");
    ZonedDateTime original = Instant.ofEpochMilli(due).atZone(zone);
    LocalDate date = Instant.ofEpochMilli(after).atZone(zone).toLocalDate();
    for (int i = 0; i < 9; i++) {
      LocalDate candidate = date.plusDays(i);
      if ("WEEKLY".equals(repeat) && (days & (1 << (candidate.getDayOfWeek().getValue() - 1))) == 0)
        continue;
      long value = candidate.atTime(original.toLocalTime()).atZone(zone).toInstant().toEpochMilli();
      if (value > after) return value;
    }
    throw new IllegalStateException("Не найден следующий повтор");
  }
}
