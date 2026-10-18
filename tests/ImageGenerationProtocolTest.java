package local.tvgpt.assistant.digests;

import org.json.*;

public final class ImageGenerationProtocolTest {
  public static void main(String[] args) throws Exception {
    String item =
        "{\"type\":\"image_generation_call\",\"status\":\"completed\",\"result\":\"aGVsbG8=\"}";
    String done =
        "data: {\"type\":\"response.output_item.done\",\"output_index\":0,\"item\":"
            + item
            + "}\n\n";
    if (ImageGenerationProtocol.completed(done) != null)
      throw new AssertionError("unfinished stream accepted");
    JSONObject response =
        ImageGenerationProtocol.completed(
            done
                + "data:"
                + " {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"output\":[]}}\n\n");
    if (!ImageGenerationProtocol.image(response).equals("aGVsbG8="))
      throw new AssertionError("lost streamed image");
    if (!ImageGenerationProtocol.image(
            new JSONObject()
                .put(
                    "output",
                    new JSONArray()
                        .put(
                            new JSONObject()
                                .put("type", "image_generation_call")
                                .put("status", "in_progress")
                                .put("result", "x"))))
        .isEmpty()) throw new AssertionError("partial accepted");
    if (!ImageGenerationProtocol.image(new JSONObject()).isEmpty())
      throw new AssertionError("empty response accepted");

    JSONObject request = ImageGenerationProtocol.request("discussion-model", "landscape");
    if (request.getJSONArray("tools").length() != 1
        || !request
            .getJSONArray("tools")
            .getJSONObject(0)
            .getString("type")
            .equals("image_generation")) throw new AssertionError("unsafe tools");
    if (request.has("previous_response_id")) throw new AssertionError("reused conversation");
    try {
      ImageGenerationProtocol.read(new java.io.ByteArrayInputStream(new byte[65]), 64);
      throw new AssertionError("wire limit bypass");
    } catch (java.io.IOException expected) {
    }
    try {
      ImageGenerationProtocol.completed(
          "data:"
              + " {\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"unsupported_tool\"}}}\n\n");
      throw new AssertionError("explicit generation rejection was hidden as uncertain output");
    } catch (ImageGenerationProtocol.Rejected expected) {
      if (!expected.code.equals("unsupported_tool")) throw new AssertionError("lost error code");
    }
    System.out.println("ImageGenerationProtocolTest passed");
  }
}
