package local.tvgpt.assistant;

final class ReminderActions {
  static String validate(String action) {
    throw new AssertionError(
        "Scheduled action creation is outside this test; existing actions must be preserved");
  }
}
