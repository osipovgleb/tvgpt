package local.tvgpt.assistant;

import org.json.JSONObject;

public final class LiveStartupTraceTest {
  public static void main(String[] args) throws Exception {
    LiveStartupTrace trace = new LiveStartupTrace();
    JSONObject event =
        new JSONObject(
            "{\"type\":\"session.started\",\"session\":{\"token\":\"PRIVATE_TOKEN\",\"instructions\":\"PRIVATE_SPEECH\"},\"audio\":\"PRIVATE_AUDIO\"}");
    String result = trace.describe("rtc", event);
    if (result.contains("PRIVATE") || !result.contains("session.started"))
      throw new AssertionError("Trace must expose structure without payloads");
    if (trace.describe("rtc", event) != null)
      throw new AssertionError("Repeated events must not flood logs");
    if (trace.describe("sideband", event) == null)
      throw new AssertionError("Both delivery paths need independent evidence");
    if (trace.describe("rtc", new JSONObject().put("type", "PRIVATE TOKEN")) != null)
      throw new AssertionError("Arbitrary event strings must not enter logs");
    for (int i = 0; i < 100; i++) trace.describe("path" + i, event);
    if (trace.describe("another", event) != null)
      throw new AssertionError("Trace must be bounded per call");
    JSONObject audio =
        new JSONObject()
            .put("type", "session.input_audio.append")
            .put("audio", java.util.Base64.getEncoder().encodeToString(new byte[] {1, 0, -1, -1}));
    for (int i = 0; i < 24; i++)
      if (trace.inputAudio(audio) != null) throw new AssertionError("Aggregate window too short");
    String level = trace.inputAudio(audio);
    if (!level.contains("rms=1.0 peak=1") || !level.contains("samples=50"))
      throw new AssertionError(
          "PCM16 signed decoding must match native microphone meter: " + level);
    if (trace.inputAudio(audio.put("audio", "not base64!")) != null)
      throw new AssertionError("Malformed audio must not break signalling");
    System.out.println(
        "LiveStartupTraceTest: payload privacy, channel distinction and bounds passed");
  }
}
