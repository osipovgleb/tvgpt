package local.tvgpt.assistant;

import java.util.*;
import local.tvgpt.assistant.settings.ModelOptions;

public final class ModelOptionsTest {
  public static void main(String[] args) {
    String json =
        "{\"models\":[{\"slug\":\"gpt-test\"},{\"slug\":\"gpt-test\"},{\"slug\":\"gpt-hidden\",\"visibility\":\"hide\"},{\"slug\":\"gpt-live-2\"},{\"slug\":\"../../escape\"}]}";
    if (!ModelOptions.parse(json, false, "gpt-default")
        .equals(Arrays.asList("gpt-default", "gpt-test")))
      throw new AssertionError("visible text models only");
    if (!ModelOptions.parse(json, true, "gpt-live-1-codex")
        .equals(Arrays.asList("gpt-live-1-codex", "gpt-live-2")))
      throw new AssertionError("live models isolated");
    if (!ModelOptions.parse("bad", false, "gpt-default").equals(Arrays.asList("gpt-default")))
      throw new AssertionError("catalog failure preserves working model");
    String ordered =
        "{\"models\":[{\"slug\":\"gpt-a\"},{\"slug\":\"gpt-b\"},{\"slug\":\"gpt-c\"}]}";
    if (!ModelOptions.parse(ordered, false, "gpt-b")
        .equals(Arrays.asList("gpt-a", "gpt-b", "gpt-c")))
      throw new AssertionError("cycling must not reorder around current selection");
    String reasoning =
        "{\"models\":[{\"slug\":\"gpt-a\",\"supported_reasoning_levels\":[{\"effort\":\"low\"},{\"effort\":\"high\"},{\"effort\":\"bad\"},{\"effort\":\"low\"}]}]}";
    if (!ModelOptions.reasoning(reasoning, "gpt-a").equals(Arrays.asList("", "low", "high")))
      throw new AssertionError("reasoning must follow selected model capabilities");
    if (!ModelOptions.reasoning(reasoning, "gpt-b").equals(Arrays.asList("")))
      throw new AssertionError("unknown model must preserve server default");
    if (!ModelOptions.fastLabel(true, "default").equals("Fast запрошен")
        || !ModelOptions.fastLabel(true, "priority").equals("Fast")
        || !ModelOptions.fastLabel(true, "").equals("Fast запрошен"))
      throw new AssertionError(
          "Subscription default echo must not assert a downgrade; explicit priority is"
              + " acknowledged");
    System.out.println(
        "Model options: visible catalog only, dedupe, Live separation and fallback passed");
  }
}
