package local.tvgpt.assistant.reminders;

public final class ReminderDeliveryTest {
  static void eq(String wanted, String got) {
    if (!wanted.equals(got)) throw new AssertionError(wanted + " != " + got);
  }

  public static void main(String[] args) {
    eq("failed", DeliveryOutcome.combine(null, false, "failed"));
    eq("notified", DeliveryOutcome.combine(null, true, "failed"));
    eq("completed", DeliveryOutcome.combine(null, false, "completed"));
    eq("unconfirmed", DeliveryOutcome.combine("unconfirmed", true, "completed"));
    eq("failed", DeliveryOutcome.combine("failed", true, "completed"));
    eq("notified", DeliveryOutcome.combine("completed", true, "failed"));
    eq("unconfirmed", DeliveryOutcome.combine(null, false, "unconfirmed"));
    System.out.println("Reminder delivery outcomes passed");
  }
}
