package local.tvgpt.assistant;

/** Parse only player configuration records, never infer state from historical audio event logs. */
final class PlaybackObservation {
  static Boolean active(String dump, int uid) {
    boolean found = false;
    String current = dump.split("Audio event log: playback", 2)[0];
    for (String line : current.split("\n")) {
      if (!line.contains("AudioPlaybackConfiguration ")
          || !line.contains("u/pid:" + uid + "/")
          || !line.contains("usage=USAGE_MEDIA")) continue;
      if (line.contains("state:started")) return true;
      if (line.contains("state:paused")
          || line.contains("state:stopped")
          || line.contains("state:idle")) found = true;
    }
    return found ? Boolean.FALSE : null;
  }
}
