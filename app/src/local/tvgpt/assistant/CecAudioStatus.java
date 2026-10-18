package local.tvgpt.assistant;

import java.util.regex.*;

/** Readback from the active CEC audio system, never the TV's unrelated stream index. */
final class CecAudioStatus {
  private static final Pattern REPORT =
      Pattern.compile(
          "(?m)^.*\\[R\\].*<Report Audio Status> 5[0-9A-Fa-f]:7[Aa]:([0-9A-Fa-f]{2}).*$");
  final String identity;
  final int percent;
  final boolean muted;

  private CecAudioStatus(String identity, int value) {
    this.identity = identity;
    percent = (value & 127) <= 100 ? value & 127 : -1;
    muted = (value & 128) != 0;
  }

  static CecAudioStatus parse(String dump) {
    Matcher matcher = REPORT.matcher(dump);
    CecAudioStatus latest = new CecAudioStatus("", 127);
    while (matcher.find())
      latest = new CecAudioStatus(matcher.group(), Integer.parseInt(matcher.group(1), 16));
    return latest;
  }

  boolean confirms(CecAudioStatus before, String direction) {
    if (identity.isEmpty() || identity.equals(before.identity) || percent < 0 || before.percent < 0)
      return false;
    return direction.equals("up") ? percent > before.percent : percent < before.percent;
  }
}
