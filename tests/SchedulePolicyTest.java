package local.tvgpt.assistant;

import java.time.*;
import local.tvgpt.assistant.reminders.SchedulePolicy;

public class SchedulePolicyTest {
  public static void main(String[] args) {
    ZoneId zone = ZoneId.of("Europe/Berlin");
    try {
      SchedulePolicy.calendar("2026-03-29T02:30", zone);
      throw new AssertionError("DST gap");
    } catch (IllegalArgumentException expected) {
    }
    long due = SchedulePolicy.calendar("2026-10-20T21:00", ZoneId.of("Europe/Moscow"));
    long next =
        SchedulePolicy.next(due, "WEEKLY", 2, ZoneId.of("Europe/Moscow"), due + 30L * 86400000);
    if (next <= due + 30L * 86400000
        || Instant.ofEpochMilli(next).atZone(ZoneId.of("Europe/Moscow")).getDayOfWeek()
            != DayOfWeek.TUESDAY) throw new AssertionError("missed repeats");
    if (SchedulePolicy.deliver(false) || !SchedulePolicy.deliver(true))
      throw new AssertionError("screen off");
    try {
      SchedulePolicy.timer(0, 100);
      throw new AssertionError("zero duration");
    } catch (IllegalArgumentException expected) {
    }
    long elapsedDue = 100000;
    long elapsedNow = 40000;
    if (SchedulePolicy.timerWallDue(9000000, elapsedNow, elapsedDue, true, 123) != 9060000)
      throw new AssertionError("clock change fired timer early");
    if (SchedulePolicy.timerWallDue(100, elapsedNow, elapsedDue, false, 123) != 123)
      throw new AssertionError("reboot fallback");
    System.out.println("Schedule policy tests passed");
  }
}
