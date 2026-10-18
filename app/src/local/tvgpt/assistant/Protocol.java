package local.tvgpt.assistant;

public final class Protocol {
  public static boolean pending(int status) {
    return status == 403 || status == 404;
  }

  public static int interval(String value) {
    try {
      return Math.min(60, Math.max(5, Integer.parseInt(value)));
    } catch (NumberFormatException e) {
      return 5;
    }
  }

  public static String callId(String location) {
    for (String part : location.split("\\?")[0].split("/")) {
      if (part.matches("rtc_[A-Za-z0-9_-]+")
          || part.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) return part;
    }
    throw new IllegalArgumentException("Голосовой сервер не вернул идентификатор звонка");
  }
}
