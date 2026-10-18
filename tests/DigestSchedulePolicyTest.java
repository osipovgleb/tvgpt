package local.tvgpt.assistant.digests;

import java.time.*;
import org.json.*;

public final class DigestSchedulePolicyTest {
  public static void main(String[] args) throws Exception {
    JSONObject s =
        new JSONObject()
            .put("repeat", "daily")
            .put("hour", 2)
            .put("minute", 30)
            .put("zone", "Europe/Berlin");
    long gap = DigestSchedulePolicy.next(s, Instant.parse("2026-03-28T23:00:00Z").toEpochMilli());
    if (gap != Instant.parse("2026-03-29T01:00:00Z").toEpochMilli())
      throw new AssertionError("DST gap");
    long overlap =
        DigestSchedulePolicy.next(s, Instant.parse("2026-10-24T22:00:00Z").toEpochMilli());
    if (overlap != Instant.parse("2026-10-25T00:30:00Z").toEpochMilli())
      throw new AssertionError("overlap");
    if (DigestSchedulePolicy.next(s, overlap)
        != Instant.parse("2026-10-26T01:30:00Z").toEpochMilli())
      throw new AssertionError("double DST delivery");
    JSONObject interval =
        new JSONObject().put("repeat", "interval").put("interval_minutes", 30).put("zone", "UTC");
    try {
      DigestSchedulePolicy.next(interval, 0);
      throw new AssertionError("too frequent");
    } catch (IllegalArgumentException expected) {
    }
    interval.put("interval_minutes", 60);
    if (DigestSchedulePolicy.slot(interval, 259200001) != 259200000)
      throw new AssertionError("burst recovery");
    System.out.println("DigestSchedulePolicyTest passed");
  }
}
