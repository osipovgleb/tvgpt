package local.tvgpt.assistant;

import java.util.Locale;

/** Decisions shared by window selection and bounded semantic node lookup. */
final class AccessibilityPolicy {
  static int windowRank(boolean focused, boolean active) {
    return focused ? 2 : active ? 1 : 0;
  }

  static int labelRank(String requested, CharSequence text, CharSequence description) {
    String query = normalized(requested);
    if (query.isEmpty()) return 0;
    String title = normalized(text), detail = normalized(description);
    if (query.equals(title) || query.equals(detail)) return 2;
    return title.contains(query) || detail.contains(query) ? 1 : 0;
  }

  static int uniqueBest(int[] ranks) {
    int best = 0, selected = -1;
    for (int i = 0; i < ranks.length; i++) {
      if (ranks[i] > best) {
        best = ranks[i];
        selected = i;
      } else if (ranks[i] == best) selected = -1;
    }
    return selected;
  }

  private static String normalized(CharSequence text) {
    return text == null ? "" : text.toString().trim().toLowerCase(Locale.ROOT);
  }
}
