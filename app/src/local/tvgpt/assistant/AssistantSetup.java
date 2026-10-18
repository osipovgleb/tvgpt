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
    String startupWarning = startup(remote) + protect(remote);
    remote.run("pm disable-user --user 0 com.google.android.katniss");
    if (remote
        .run("pm list packages -d com.google.android.katniss")
        .contains("package:com.google.android.katniss"))
      return "TV GPT назначен системным помощником. Голосовой Google отключён. Проверьте кнопку"
          + " пульта и микрофон."
          + startupWarning;
    String output =
        remote.run(
            "am start -W -a com.google.android.assistant.HOTWORD_DISABLE -p"
                + " com.google.android.katniss");
    if (output.contains("Error") || output.contains("Exception"))
      return "TV GPT назначен. Отключите голосовой вызов Google в настройках телевизора."
          + startupWarning;
    return "TV GPT назначен системным помощником. Команда отключения голосового вызова Google"
        + " отправлена. Проверьте кнопку пульта и микрофон."
        + startupWarning;
  }

  // TCL's separate startup appops can block even the selected system assistant.
  // Query support first: AOSP and other vendors need no speculative permission changes.
  static String startup(Remote remote) throws Exception {
    boolean changed = false;
    for (String operation : new String[] {"APP_AUTO_START", "APP_ASSOC_START"}) {
      String get =
          "cmd appops get --user 0 local.tvgpt.assistant "
              + operation
              + " 2>/dev/null | grep '^"
              + operation
              + ":'";
      // Unknown operations print Error:, which the ADB transport correctly rejects.
      // Filter only this optional capability probe; socket/transport failures still throw.
      String before = remote.run(get);
      if (!before.matches("(?s).*\\b" + operation + ":\\s+(allow|ignore|deny|default)\\b.*"))
        continue;
      if (!before.contains(operation + ": allow")) {
        remote.run("cmd appops set --user 0 local.tvgpt.assistant " + operation + " allow");
        changed = true;
        if (!remote.run(get).contains(operation + ": allow"))
          return " Разрешите фоновый запуск TV GPT в настройках телевизора.";
      }
    }
    if (changed) remote.run("cmd appops write-settings");
    return "";
  }

  static String protect(Remote remote) throws Exception {
    String service = "dumpsys activity service com.tcl.guard/.memorymanager.TGuardService";
    String query = service + " | grep 'KillWhiteList:.*local.tvgpt.assistant$'";
    if (remote.run(query).contains("local.tvgpt.assistant")) return "";
    if (!remote
        .run(service + " --help | grep 'add KillWhiteList value'")
        .contains("add KillWhiteList value")) return "";
    // TGuard documents --ma as append, preserving all existing whitelist entries.
    remote.run(
        service + " --ma mKillWhiteList local.tvgpt.assistant | grep 'local.tvgpt.assistant'");
    return remote.run(query).contains("local.tvgpt.assistant")
        ? ""
        : " Разрешите фоновую работу TV GPT в настройках телевизора.";
  }
}
