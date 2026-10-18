package local.tvgpt.assistant.settings;

public enum SpeechPreset {
  NORMAL("Обычный", "Говори естественно, в обычном темпе.", 1),
  QUIET("Тихий", "Говори мягко и спокойно, без восклицаний.", .35),
  FAST("Быстрый", "Говори быстро, кратко и чётко.", 1),
  CALM("Спокойный", "Говори неторопливо, с короткими паузами.", 1),
  DETAILED("Подробный", "Объясняй подробно и последовательно, когда это полезно.", 1),
  ENERGETIC("Энергичный", "Говори живо, энергично и доброжелательно.", 1);
  public final String label, instructions;
  public final double outputGain;

  SpeechPreset(String label, String instructions, double gain) {
    this.label = label;
    this.instructions = instructions;
    outputGain = gain;
  }

  public static SpeechPreset parse(String name) {
    try {
      return valueOf(name);
    } catch (Exception e) {
      return NORMAL;
    }
  }
}
