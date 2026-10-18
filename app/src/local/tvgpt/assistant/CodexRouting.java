package local.tvgpt.assistant;

/** Match Codex's subscription routing hint to the exact model/tier in this request. */
final class CodexRouting {
  static String hint(String model, String tier) {
    if (!model.matches("[a-zA-Z0-9][a-zA-Z0-9_.:-]{1,95}")
        || !(tier.equals("priority") || tier.equals("default")))
      throw new IllegalArgumentException("Invalid model routing");
    return "model=" + model + ";tier=" + tier;
  }
}
