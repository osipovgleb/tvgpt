package local.tvgpt.assistant;

import java.time.*;
import local.tvgpt.assistant.reminders.RemoteDateTime;

public final class RemoteDateTimeTest {
  public static void main(String[] args) {
    check(
        RemoteDateTime.month(LocalDate.of(2024, 1, 31), 1).equals(LocalDate.of(2024, 2, 29)),
        "leap month clamp");
    check(
        RemoteDateTime.year(LocalDate.of(2024, 2, 29), 1).equals(LocalDate.of(2025, 2, 28)),
        "leap year clamp");
    check(
        RemoteDateTime.month(LocalDate.of(2025, 12, 31), 1).equals(LocalDate.of(2026, 1, 31)),
        "year rollover");
    check(
        RemoteDateTime.hour(LocalTime.of(23, 47), 1).equals(LocalTime.of(0, 47)),
        "hour wraps without minute change");
    check(
        RemoteDateTime.minute(LocalTime.of(3, 0), -1).equals(LocalTime.of(3, 59)),
        "minute wraps without changing hour");
    check(RemoteDateTime.firstCell(LocalDate.of(2026, 2, 1)) == 6, "Monday calendar origin");
    check(
        RemoteDateTime.zone("bad-zone", ZoneId.of("Europe/Moscow")).getId().equals("Europe/Moscow"),
        "bad zone fallback");
    check(
        RemoteDateTime.zone("Asia/Tokyo", ZoneOffset.UTC).getId().equals("Asia/Tokyo"),
        "selected timezone");
    check(
        RemoteDateTime.zone(null, ZoneOffset.UTC).equals(ZoneOffset.UTC),
        "system default until selected");
    System.out.println(
        "Remote date/time: leap dates, calendar origin, independent time fields and timezone"
            + " fallback passed");
  }

  private static void check(boolean value, String label) {
    if (!value) throw new AssertionError(label);
  }
}
