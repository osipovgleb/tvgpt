package local.tvgpt.assistant.data;

public class KnowledgeRepository {
  public KnowledgeRepository(android.content.Context c) {}

  public String context() {
    throw new AssertionError("Saved memory was read without an explicit retrieval tool request");
  }
}
