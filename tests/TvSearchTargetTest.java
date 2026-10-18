package local.tvgpt.assistant;

import android.content.Context;
import org.json.JSONObject;

public final class TvSearchTargetTest {
  private static final class Screen extends TvAccessibility {
    String pkg = "original.app";

    @Override
    JSONObject observation() throws Exception {
      return new JSONObject().put("package", pkg).put("text", "search results");
    }
  }

  public static void main(String[] args) throws Exception {
    Screen screen = new Screen();
    TvAccessibility.current = screen;
    JSONObject result =
        new JSONObject(
            new TvActionObserver(new Context())
                .execute(
                    "search_app",
                    new JSONObject().put("name", "current").put("query", "movie"),
                    () -> {
                      screen.pkg = "unrelated.app";
                      return "Команда поиска передана приложению";
                    }));
    if (!result.optString("status").equals("unconfirmed"))
      throw new AssertionError("new foreground app falsely confirmed original search: " + result);
    if (!result.optString("confirmed_scope").isEmpty())
      throw new AssertionError("wrong-package evidence accepted");
    System.out.println("TvSearchTargetTest passed");
  }
}
