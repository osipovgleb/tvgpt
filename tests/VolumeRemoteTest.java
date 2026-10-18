package local.tvgpt.assistant;

import org.json.JSONObject;

public final class VolumeRemoteTest {
  public static void main(String[] args) throws Exception {
    String first = "[R] time=2026-10-18 16:55:38 message=<Report Audio Status> 50:7A:1B";
    CecAudioStatus baseline = CecAudioStatus.parse(first);
    if (CecAudioStatus.parse(first.replace("50:7A", "54:7A")).percent != 27)
      throw new AssertionError("Readback incorrectly tied to TV logical address");
    if (baseline.percent != 27 || baseline.muted) throw new AssertionError("Bad CEC level");
    CecAudioStatus up =
        CecAudioStatus.parse(
            first + "\n[R] time=2026-10-18 16:56:00 message=<Report Audio Status> 50:7A:1E");
    if (!up.confirms(baseline, "up") || up.confirms(baseline, "down"))
      throw new AssertionError("Wrong direction confirmed");
    if (baseline.confirms(baseline, "up") || CecAudioStatus.parse("").confirms(baseline, "up"))
      throw new AssertionError("Stale or missing evidence confirmed");
    CecAudioStatus unknown = CecAudioStatus.parse(first.replace("1B", "7F"));
    if (unknown.percent != -1 || unknown.confirms(baseline, "up"))
      throw new AssertionError("Unknown CEC volume confirmed");
    if (!CecAudioStatus.parse(first.replace("1B", "9B")).muted)
      throw new AssertionError("Mute flag ignored");
    if (VolumeRemote.execute("/app.apk", "up", cmd -> "{\"route\":\"android\"}") != null)
      throw new AssertionError("Normal Android target lost");
    int[] sent = {0};
    String observed =
        VolumeRemote.execute(
            "/app.apk",
            "down",
            cmd -> {
              sent[0]++;
              if (!cmd.endsWith(" down")) throw new AssertionError("Wrong key");
              return "{\"route\":\"hdmi_system_audio\",\"status\":\"observed\"}";
            });
    if (sent[0] != 1 || !new JSONObject(observed).optString("status").equals("observed"))
      throw new AssertionError("External evidence lost");
    for (String output :
        new String[] {"", "junk", "{\"route\":\"unknown\",\"status\":\"unconfirmed\"}"})
      if (VolumeRemote.execute("/app.apk", "up", cmd -> output) == null)
        throw new AssertionError("Unknown dispatch fell back to another volume target");
    String failed =
        VolumeRemote.execute(
            "/app.apk",
            "up",
            cmd -> {
              throw new java.io.IOException();
            });
    if (!new JSONObject(failed).optString("status").equals("unconfirmed"))
      throw new AssertionError("Wire failure confirmed");
    try {
      VolumeRemote.execute(
          "/app.apk",
          "up",
          cmd -> {
            throw new java.util.concurrent.CancellationException();
          });
      throw new AssertionError("Cancellation swallowed");
    } catch (java.util.concurrent.CancellationException expected) {
    }
    System.out.println(
        "Volume remote: target routing, no uncertain retry, fresh CEC evidence passed");
  }
}
