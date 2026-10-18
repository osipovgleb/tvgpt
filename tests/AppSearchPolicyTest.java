package local.tvgpt.assistant;

public final class AppSearchPolicyTest {
  public static void main(String[] args) {
    check(
        AppSearchPolicy.matches("android:id/search_src_text", null, null, null), "standard editor");
    check(AppSearchPolicy.matches("some.app:id/query", null, null, null), "query id");
    check(AppSearchPolicy.matches(null, "Поиск", null, null), "Russian label");
    check(AppSearchPolicy.matches(null, null, "Search…", null), "English hint");
    check(AppSearchPolicy.matches(null, null, null, "Найти"), "description");
    check(
        !AppSearchPolicy.matches("some.app:id/research_result", "My search history", null, null),
        "not results");
    check(!AppSearchPolicy.matches("some.app:id/password", "Пароль", null, null), "not password");
    check(!AppSearchPolicy.matches(null, null, null, null), "not arbitrary field");
    System.out.println("AppSearchPolicyTest passed");
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
