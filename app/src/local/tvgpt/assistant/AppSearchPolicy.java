package local.tvgpt.assistant;

import java.util.Locale;

/** Conservative semantic matching; a random focused editor is never a search field. */
final class AppSearchPolicy {
  static boolean matches(
      String id, CharSequence text, CharSequence hint, CharSequence description) {
    String leaf = id == null ? "" : id.substring(id.lastIndexOf('/') + 1);
    return leaf.equals("search_src_text")
        || leaf.equals("search")
        || leaf.equals("query")
        || leaf.equals("search_query")
        || leaf.equals("search_button")
        || leaf.equals("search_mag_icon")
        || label(text)
        || label(hint)
        || label(description);
  }

  private static boolean label(CharSequence value) {
    if (value == null) return false;
    String text =
        value.toString().trim().toLowerCase(Locale.ROOT).replaceAll("[.\\u2026:]+$", "").trim();
    return text.equals("search") || text.equals("поиск") || text.equals("найти");
  }
}
