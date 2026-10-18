package local.tvgpt.assistant.data;

public final class DataPolicy {
  private DataPolicy() {}

  public static String turnKey(String call, String event, long sequence) {
    return call + ":" + (event == null || event.isEmpty() ? "local-" + sequence : event);
  }

  public static void note(String title, String text) {
    if (title == null
        || text == null
        || title.trim().isEmpty()
        || text.trim().isEmpty()
        || title.codePointCount(0, title.length()) > 120
        || text.codePointCount(0, text.length()) > 4000)
      throw new IllegalArgumentException("Нужны заголовок до 120 и текст до 4000 символов");
  }
}
