package local.tvgpt.assistant;

import android.content.Context;
import local.tvgpt.assistant.digests.*;
import org.json.*;

final class DigestTools {
  static void schemas(JSONArray out) throws Exception {
    JSONArray tools = DigestToolContract.schemas();
    for (int i = 0; i < tools.length(); i++) out.put(tools.get(i));
  }

  static String execute(Context c, String name, JSONObject args) throws Exception {
    return execute(c, name, args, CancellationToken.NONE);
  }

  static String execute(Context c, String name, JSONObject args, CancellationToken token)
      throws Exception {
    return new DigestCommands(c).execute(name, args, token).toString();
  }
}
