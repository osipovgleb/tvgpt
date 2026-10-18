package local.tvgpt.assistant;

public class AccessibilityPolicyTest {
  public static void main(String[] args) {
    if (AccessibilityPolicy.windowRank(true, false) <= AccessibilityPolicy.windowRank(false, true))
      throw new AssertionError("Input-focused app must outrank a merely active window");
    if (AccessibilityPolicy.windowRank(false, true) <= AccessibilityPolicy.windowRank(false, false))
      throw new AssertionError("Active window must outrank a background app");
    int exact = AccessibilityPolicy.labelRank("Смотреть позже", "Смотреть позже", null);
    int partial =
        AccessibilityPolicy.labelRank("Смотреть позже", "Добавить в Смотреть позже", null);
    int description = AccessibilityPolicy.labelRank("Смотреть позже", null, "СМОТРЕТЬ ПОЗЖЕ");
    if (exact != 2 || description != exact || partial != 1)
      throw new AssertionError("Exact text or description must outrank a substring");
    if (AccessibilityPolicy.uniqueBest(new int[] {partial, exact}) != 1
        || AccessibilityPolicy.uniqueBest(new int[] {exact, exact}) != -1
        || AccessibilityPolicy.uniqueBest(new int[] {0, 0}) != -1)
      throw new AssertionError("Ambiguous labels must not cause an arbitrary click");
    if (AccessibilityPolicy.labelRank("  ", "anything", null) != 0
        || AccessibilityPolicy.labelRank("Смотреть позже", "Смотреть сейчас", null) != 0)
      throw new AssertionError("Empty or unrelated labels must not match");
    System.out.println(
        "AccessibilityPolicyTest: foreground windows, exact descriptions and ambiguity passed");
  }
}
