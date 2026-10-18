package local.tvgpt.assistant;

/** Fixed local ADB operations for the welcome-page assistant handover button. */
final class AssistantSetup {
  interface Remote {
    String run(String command) throws Exception;
  }

  static String configure(Remote remote) throws Exception {
    remote.run(
        "cmd role add-role-holder --user 0 android.app.role.ASSISTANT local.tvgpt.assistant 0");
    remote.run("settings put secure assistant local.tvgpt.assistant/.AssistantService");
    remote.run(
        "settings put secure voice_interaction_service local.tvgpt.assistant/.AssistantService");
    for (String key : new String[] {"assistant", "voice_interaction_service"}) {
      if (!remote
          .run("settings get secure " + key)
          .trim()
          .equals("local.tvgpt.assistant/.AssistantService"))
        throw new IllegalStateException("Телевизор не назначил TV GPT помощником");
    }
    remote.run("pm disable-user --user 0 com.google.android.katniss");
    if (remote
        .run("pm list packages -d com.google.android.katniss")
        .contains("package:com.google.android.katniss"))
      return "TV GPT назначен системным помощником. Голосовой Google отключён. Проверьте кнопку"
          + " пульта и микрофон.";
    String output =
        remote.run(
            "am start -W -a com.google.android.assistant.HOTWORD_DISABLE -p"
                + " com.google.android.katniss");
    if (output.contains("Error") || output.contains("Exception"))
      return "TV GPT назначен. Отключите голосовой вызов Google в настройках телевизора.";
    return "TV GPT назначен системным помощником. Команда отключения голосового вызова Google"
        + " отправлена. Проверьте кнопку пульта и микрофон.";
  }
}
