package local.tvgpt.assistant.reminders;

/** Notification submission is weaker evidence than the TTS completion callback. */
final class DeliveryOutcome {
  private DeliveryOutcome() {}

  static String combine(String action, boolean notified, String speech) {
    if ("failed".equals(action)) return "failed";
    if ("unconfirmed".equals(action)) return "unconfirmed";
    if ("completed".equals(speech)) return "completed";
    if (notified) return "notified";
    return "unconfirmed".equals(speech) ? "unconfirmed" : "failed";
  }
}
