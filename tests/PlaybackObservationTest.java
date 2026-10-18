package local.tvgpt.assistant;

public final class PlaybackObservationTest {
  public static void main(String[] args) {
    String head = "AudioPlaybackConfiguration piid:1 u/pid:10064/24 state:";
    String tail = " attr:AudioAttributes: usage=USAGE_MEDIA\n";
    if (!Boolean.TRUE.equals(PlaybackObservation.active(head + "started" + tail, 10064)))
      throw new AssertionError("Active target missed");
    if (!Boolean.FALSE.equals(PlaybackObservation.active(head + "paused" + tail, 10064)))
      throw new AssertionError("Paused target missed");
    if (PlaybackObservation.active(head + "started" + tail, 1006) != null)
      throw new AssertionError("UID prefix matched another app");
    if (PlaybackObservation.active("Audio event log: playback\n" + head + "started" + tail, 10064)
        != null) throw new AssertionError("Historical player treated as active");
    if (PlaybackObservation.active("", 10064) != null)
      throw new AssertionError("Absent player treated as paused");
    System.out.println(
        "Playback observations: exact app, paused, absent and historical records passed");
  }
}
