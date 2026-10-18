package local.tvgpt.assistant.digests;

import org.json.*;

public final class DigestToolsTest {
  public static void main(String[] args) throws Exception {
    JSONArray tools = DigestToolContract.schemas();
    boolean refresh = false;
    for (int i = 0; i < tools.length(); i++) {
      JSONObject tool = tools.getJSONObject(i), p = tool.getJSONObject("parameters");
      if (!tool.getBoolean("strict") || p.getBoolean("additionalProperties"))
        throw new AssertionError("unsafe schema");
      if (tool.getString("name").equals("digest_refresh")) refresh = true;
      if (p.getJSONObject("properties").length() != p.getJSONArray("required").length())
        throw new AssertionError("strict keys");
    }
    if (!refresh) throw new AssertionError("no refresh tool");
    try {
      DigestToolContract.validate("digest_shell", new JSONObject());
      throw new AssertionError("arbitrary execution");
    } catch (IllegalArgumentException expected) {
    }
    try {
      DigestToolContract.validate(
          "digest_refresh", new JSONObject().put("digest_id", "bad").put("operation_id", "bad"));
      throw new AssertionError("bad uuid");
    } catch (IllegalArgumentException expected) {
    }
    System.out.println("DigestToolsTest passed");
  }
}
