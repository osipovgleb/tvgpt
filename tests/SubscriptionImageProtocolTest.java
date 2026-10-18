package local.tvgpt.assistant.digests;

import java.io.*;
import org.json.*;

public final class SubscriptionImageProtocolTest {
  public static void main(String[] args) throws Exception {
    JSONObject request =
        SubscriptionImageProtocol.request("Landscape volleyball editorial illustration");
    if (!request.getString("model").equals("gpt-image-2")
        || request.has("input")
        || request.has("tools"))
      throw new AssertionError("standalone image request must not use a text Responses envelope");
    JSONObject response =
        SubscriptionImageProtocol.read(
            new ByteArrayInputStream(
                "{\"created\":123,\"data\":[{\"b64_json\":\"aGVsbG8=\"}]}".getBytes("UTF-8")),
            1024);
    if (!SubscriptionImageProtocol.image(response).equals("aGVsbG8="))
      throw new AssertionError("image data lost");
    if (!SubscriptionImageProtocol.image(
            new JSONObject()
                .put(
                    "data",
                    new JSONArray()
                        .put(new JSONObject().put("url", "https://example.org/untrusted.png"))))
        .isEmpty()) throw new AssertionError("external URL treated as an inline generated image");
    try {
      SubscriptionImageProtocol.read(new ByteArrayInputStream(new byte[65]), 64);
      throw new AssertionError("image wire bound bypass");
    } catch (IOException expected) {
    }
    try {
      SubscriptionImageProtocol.read(
          new ByteArrayInputStream(
              "{\"error\":{\"code\":\"usage_limit_reached\",\"message\":\"private server text\"}}"
                  .getBytes("UTF-8")),
          1024);
      throw new AssertionError("explicit image rejection hidden");
    } catch (ImageGenerationProtocol.Rejected expected) {
      if (!expected.code.equals("usage_limit_reached") || expected.getMessage().contains("private"))
        throw new AssertionError("unsafe or missing error details");
    }
    System.out.println("SubscriptionImageProtocolTest passed");
  }
}
