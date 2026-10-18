package local.tvgpt.assistant;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded protocol shape diagnostics. Never includes string contents or audio payloads. */
final class LiveStartupTrace {
  private final Set<String> seen = new HashSet<>();
  private long samples;
  private int frames, peak;
  private double energy;

  synchronized String inputAudio(JSONObject event) {
    if (!"session.input_audio.append".equals(event.optString("type"))) return null;
    String encoded = event.optString("audio");
    if (encoded.isEmpty() || encoded.length() > 65536) return null;
    byte[] bytes;
    try {
      bytes = java.util.Base64.getDecoder().decode(encoded);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
    // The Live PCM16 payload is already delivered by the existing sideband socket.
    // Inspect it transiently; retain only aggregate numbers, never the audio itself.
    for (int i = 0; i + 1 < bytes.length; i += 2) {
      int sample = (short) ((bytes[i] & 255) | (bytes[i + 1] << 8));
      peak = Math.max(peak, Math.abs(sample));
      energy += (double) sample * sample;
      samples++;
    }
    if (++frames < 25 || samples == 0) return null;
    String result =
        "server input frames="
            + frames
            + " samples="
            + samples
            + " pcm16 rms="
            + Math.sqrt(energy / samples)
            + " peak="
            + peak;
    samples = 0;
    frames = peak = 0;
    energy = 0;
    return result;
  }

  synchronized String describe(String origin, JSONObject event) {
    String type = event.optString("type");
    if (!type.matches("[a-z_.]{1,80}") || seen.size() >= 40 || !seen.add(origin + ":" + type))
      return null;
    return origin + " " + type + " " + shape(event, 0);
  }

  private String shape(Object value, int depth) {
    if (value instanceof JSONObject) {
      if (depth >= 3) return "object";
      JSONObject object = (JSONObject) value;
      StringBuilder result = new StringBuilder("{");
      Iterator<String> keys = object.keys();
      int count = 0;
      while (keys.hasNext() && count++ < 16) {
        String key = keys.next();
        // Protocol keys only; never print arbitrary application/user data as keys.
        if (!key.matches("[a-z_]{1,64}")) continue;
        if (result.length() > 1) result.append(',');
        result.append(key).append(':').append(shape(object.opt(key), depth + 1));
      }
      return result.append('}').toString();
    }
    if (value instanceof JSONArray) {
      JSONArray array = (JSONArray) value;
      return "array["
          + array.length()
          + "]"
          + (array.length() == 0 || depth >= 3 ? "" : shape(array.opt(0), depth + 1));
    }
    if (value instanceof String) return "string[" + ((String) value).length() + "]";
    if (value instanceof Number) return "number";
    if (value instanceof Boolean) return "boolean";
    return "null";
  }
}
