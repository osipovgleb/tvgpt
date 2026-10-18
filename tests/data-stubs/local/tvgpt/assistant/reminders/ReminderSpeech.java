package local.tvgpt.assistant.reminders;

public final class ReminderSpeech {
  public static java.util.function.Consumer<String> pending;
  public static final java.util.List<String> bodies = new java.util.ArrayList<>();

  public static void speak(
      android.content.Context c, String text, java.util.function.Consumer<String> done) {
    bodies.add(text);
    pending = done;
  }
}
