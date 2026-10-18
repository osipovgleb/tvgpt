package local.tvgpt.assistant;

import java.util.*;

public class AssistantSetupTest {
  public static void main(String[] args) throws Exception {
    List<String> calls = new ArrayList<>();
    String result =
        AssistantSetup.configure(
            command -> {
              calls.add(command);
              return command.startsWith("settings get")
                  ? "local.tvgpt.assistant/.AssistantService\n"
                  : command.startsWith("pm list packages -d")
                      ? "package:com.google.android.katniss\n"
                      : "";
            });
    if (!result.contains("отключён")
        || calls.stream()
            .noneMatch(s -> s.equals("pm disable-user --user 0 com.google.android.katniss")))
      throw new AssertionError("Setup omitted verified Google voice handover");
    try {
      AssistantSetup.configure(
          command -> command.startsWith("settings get") ? "com.google.android.katniss/.Old" : "");
      throw new AssertionError("Claimed success without verified default assistant");
    } catch (IllegalStateException expected) {
    }
    List<String> startup = new ArrayList<>();
    Set<String> allowed = new HashSet<>();
    String startupResult =
        AssistantSetup.startup(
            command -> {
              startup.add(command);
              String operation =
                  command.contains("APP_AUTO_START") ? "APP_AUTO_START" : "APP_ASSOC_START";
              if (command.startsWith("cmd appops set")) {
                String[] parts = command.split(" ");
                allowed.add(parts[parts.length - 2]);
                return "";
              }
              return operation + ": " + (allowed.contains(operation) ? "allow" : "ignore");
            });
    if (allowed.size() != 2 || !startupResult.isEmpty())
      throw new AssertionError("OEM startup permissions not enabled and verified");
    AssistantSetup.startup(
        command -> {
          if (command.startsWith("cmd appops get") && !command.contains("| grep "))
            throw new java.io.IOException("ADB rejects raw Error: Unknown operation output");
          return "";
        });
    List<String> unsupported = new ArrayList<>();
    AssistantSetup.startup(
        command -> {
          unsupported.add(command);
          return "Error: Unknown operation";
        });
    if (unsupported.stream().anyMatch(command -> command.startsWith("cmd appops set")))
      throw new AssertionError("Unsupported OEM operation was modified");
    if (AssistantSetup.startup(command -> "APP_AUTO_START: ignore").isEmpty())
      throw new AssertionError("Startup readback failure hidden");
    List<String> guardCalls = new ArrayList<>();
    boolean[] guardProtected = {false};
    AssistantSetup.protect(
        command -> {
          guardCalls.add(command);
          if (command.contains("--ma")) guardProtected[0] = true;
          return command.contains("--help")
              ? "add KillWhiteList value"
              : guardProtected[0] ? "KillWhiteList: local.tvgpt.assistant" : "";
        });
    if (guardCalls.stream().noneMatch(c -> c.contains("--ma mKillWhiteList local.tvgpt.assistant")))
      throw new AssertionError("TCL guard protection missing");
    List<String> absentGuard = new ArrayList<>();
    AssistantSetup.protect(
        command -> {
          absentGuard.add(command);
          return "";
        });
    if (absentGuard.stream().anyMatch(c -> c.contains("--ma")))
      throw new AssertionError("Modified unsupported guard service");
    List<String> protectedGuard = new ArrayList<>();
    AssistantSetup.protect(
        command -> {
          protectedGuard.add(command);
          return "KillWhiteList: local.tvgpt.assistant";
        });
    if (protectedGuard.stream().anyMatch(c -> c.contains("--ma")))
      throw new AssertionError("Repeated existing whitelist mutation");
    System.out.println("AssistantSetupTest: handover and default-assistant verification passed");
  }
}
