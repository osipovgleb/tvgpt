package local.tvgpt.assistant.reminders;

import java.time.*;

/** Date arithmetic for independent remote-controlled fields, without Android dependencies. */
public final class RemoteDateTime {
  private RemoteDateTime() {}

  public static LocalDate month(LocalDate value, int delta) {
    return value.plusMonths(delta);
  }

  public static LocalDate year(LocalDate value, int delta) {
    return value.plusYears(delta);
  }

  public static int firstCell(LocalDate value) {
    return value.withDayOfMonth(1).getDayOfWeek().getValue() - 1;
  }

  public static LocalTime hour(LocalTime value, int delta) {
    return value.withHour(Math.floorMod(value.getHour() + delta, 24));
  }

  public static LocalTime minute(LocalTime value, int delta) {
    return value.withMinute(Math.floorMod(value.getMinute() + delta, 60));
  }

  public static ZoneId zone(String value, ZoneId fallback) {
    if (value == null || value.isEmpty()) return fallback;
    try {
      return ZoneId.of(value);
    } catch (DateTimeException e) {
      return fallback;
    }
  }
}
