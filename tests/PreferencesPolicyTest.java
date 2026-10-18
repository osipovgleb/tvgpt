package local.tvgpt.assistant;

import local.tvgpt.assistant.settings.*;

public final class PreferencesPolicyTest {
  public static void main(String[] args) {
    if (OrbPalette.IDS.size() != 12 || new java.util.HashSet<>(OrbPalette.IDS).size() != 12)
      throw new AssertionError("12 colours");
    if (OrbPalette.colour("invalid") != OrbPalette.colour("sky"))
      throw new AssertionError("default");
    if (SpeechPreset.values().length != 6
        || SpeechPreset.parse("broken") != SpeechPreset.NORMAL
        || SpeechPreset.QUIET.outputGain != .35) throw new AssertionError("presets");
    for (SpeechPreset p : SpeechPreset.values())
      if (p.instructions.isEmpty()) throw new AssertionError("instructions");
    try {
      OrbPalette.IDS.set(0, "changed");
      throw new AssertionError("mutable palette");
    } catch (UnsupportedOperationException expected) {
    }
    for (String id : OrbPalette.IDS) {
      float[] matrix = OrbPalette.matrix(id);
      for (float value : matrix)
        if (!Float.isFinite(value)) throw new AssertionError("invalid matrix");
      for (int row = 0; row < 3; row++) {
        float white =
            matrix[row * 5]
                + matrix[row * 5 + 1]
                + matrix[row * 5 + 2]
                + matrix[row * 5 + 4] / 255f;
        if (Math.abs(white - 1) > .0001) throw new AssertionError("cloud whites changed");
      }
    }
    System.out.println("Preferences policy tests passed");
  }
}
