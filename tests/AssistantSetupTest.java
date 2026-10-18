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
    System.out.println("AssistantSetupTest: handover and default-assistant verification passed");
  }
}
