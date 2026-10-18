package local.tvgpt.assistant;

import org.json.JSONObject;

/** Select the current system-audio target before dispatch; never retry an uncertain mutation. */
final class VolumeRemote {
  interface Shell {
    String run(String command) throws Exception;
  }

  static String execute(String apk, String direction, Shell shell) throws Exception {
    if (!direction.equals("up") && !direction.equals("down"))
      throw new IllegalArgumentException("Unsupported volume direction");
    String command =
        "CLASSPATH='"
            + apk.replace("'", "'\\''")
            + "' app_process /system/bin local.tvgpt.assistant.CecVolumeCommand "
            + direction;
    try {
      JSONObject result = new JSONObject(shell.run(command).trim());
      // Only an explicit no-system-audio result permits the normal Android stream path.
      if (result.optString("route").equals("android")) return null;
      return result.toString();
    } catch (java.util.concurrent.CancellationException cancelled) {
      throw cancelled;
    } catch (InterruptedException cancelled) {
      Thread.currentThread().interrupt();
      throw cancelled;
    } catch (Exception uncertain) {
      return ActionObservation.result(
              "Результат изменения громкости не подтверждён", new JSONObject(), "")
          .put("requires_visual", false)
          .put("dispatch_status", "uncertain")
          .put("error", uncertain.getClass().getSimpleName())
          .toString();
    }
  }
}
