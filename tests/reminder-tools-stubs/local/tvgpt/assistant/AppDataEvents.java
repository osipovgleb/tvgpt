package local.tvgpt.assistant;

public final class AppDataEvents {
  public static final String REMINDERS = "reminders";
  public static int notifications;

  public static void changed(android.content.Context c, String section) {
    if (!section.equals(REMINDERS)) throw new AssertionError("Wrong data section");
    notifications++;
  }
}
