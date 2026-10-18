package local.tvgpt.assistant.settings;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Candidates, not an entitlement claim: only successful subscription playback selects a voice. */
public final class VoiceCatalogue {
  public static final String DEFAULT = "cove";
  public static final List<String> CANDIDATES =
      Collections.unmodifiableList(
          Arrays.asList(
              "cove", "juniper", "maple", "spruce", "ember", "vale", "breeze", "arbor", "sol"));

  private VoiceCatalogue() {}
}
