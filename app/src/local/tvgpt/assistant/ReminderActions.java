package local.tvgpt.assistant;

import android.content.Context;
import org.json.*;

public final class ReminderActions {
  private ReminderActions() {}

  public static String validate(String raw) throws Exception {
    JSONObject a = new JSONObject(raw);
    String name = a.getString("tool");
    JSONObject args = a.getJSONObject("arguments");
    if (name.equals("open_app")) {
      if (args.getString("name").trim().isEmpty())
        throw new IllegalArgumentException("Название приложения");
    } else if (name.equals("volume")) {
      if (!java.util.Arrays.asList("up", "down").contains(args.getString("direction")))
        throw new IllegalArgumentException("Направление");
    } else throw new IllegalArgumentException("В расписании доступны только open_app и volume");
    JSONObject clean = new JSONObject();
    if (name.equals("open_app")) clean.put("name", args.getString("name"));
    else clean.put("direction", args.getString("direction"));
    return new JSONObject().put("tool", name).put("arguments", clean).toString();
  }

  public static String execute(Context c, String raw) throws Exception {
    JSONObject a = new JSONObject(validate(raw));
    Object result = new TvTools(c).execute(a.getString("tool"), a.getJSONObject("arguments"));
    JSONObject observed = new JSONObject(String.valueOf(result));
    String expected = a.getString("tool").equals("open_app") ? "app_visible" : "volume_readback";
    if (observed.optString("status").equals("rejected")) return "failed";
    return expected.equals(observed.optString("confirmed_scope")) ? "completed" : "unconfirmed";
  }
}
