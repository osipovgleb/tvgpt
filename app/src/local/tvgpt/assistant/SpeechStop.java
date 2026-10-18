package local.tvgpt.assistant;

import java.util.LinkedHashMap;
import org.json.JSONObject;

/** Recognize explicit close commands before the cloud finishes the entire turn. */
final class SpeechStop {
  private final LinkedHashMap<String, Long> recent = new LinkedHashMap<>();
  private String partial = "";
  private long deadline = Long.MAX_VALUE;
  private boolean ended;

  synchronized boolean accept(JSONObject event, long now) {
    if (ended) return false;
    String type = event.optString("type");
    if (LiveProtocol.endsConversation(event)) {
      ended = true;
      return true;
    }
    if (type.equals("turn.done")) {
      JSONObject turn = event.optJSONObject("turn");
      if (turn != null && turn.optString("role").equals("user")) {
        partial = "";
        deadline = Long.MAX_VALUE;
      }
      return false;
    }
    String delta;
    if (type.equals("input_transcript.added")) {
      JSONObject item = event.optJSONObject("item");
      if (item == null) return false;
      delta = item.optString("text");
    } else if (type.equals("session.input_transcript.delta")) delta = event.optString("delta");
    else return false;
    String eventId = event.optString("event_id");
    JSONObject item = event.optJSONObject("item");
    String itemId = item == null ? "" : item.optString("id");
    boolean identified =
        !eventId.isEmpty() || !itemId.isEmpty() || event.has("start_ms") || event.has("end_ms");
    String key =
        !eventId.isEmpty()
            ? type + ":" + eventId
            : identified
                ? type
                    + ":"
                    + itemId
                    + ":"
                    + event.optLong("start_ms", -1)
                    + ":"
                    + event.optLong("end_ms", -1)
                    + ":"
                    + delta
                : event.toString();
    Long previous = recent.put(key, now);
    if (recent.size() > 64) recent.remove(recent.keySet().iterator().next());
    if (previous != null && (identified || now - previous < 250)) return false;
    partial += delta;
    if (partial.length() > 1500) partial = partial.substring(partial.length() - 1500);
    deadline = EndPhrase.explicit(partial) ? now + 450 : Long.MAX_VALUE;
    return false;
  }

  synchronized long delay(long now) {
    return deadline == Long.MAX_VALUE ? -1 : Math.max(0, deadline - now);
  }

  synchronized boolean due(long now) {
    if (ended || deadline == Long.MAX_VALUE || now < deadline) return false;
    ended = true;
    return true;
  }
}
