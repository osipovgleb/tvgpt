package local.tvgpt.assistant;

import local.tvgpt.assistant.data.ConversationRepository;

public final class ConversationSummarizerTest {
  public static void main(String[] args) {
    android.content.Context c = new android.content.Context();
    ConversationRepository.empty = true;
    ConversationSummarizer.run(c, "empty", 0);
    if (Http.requests != 0)
      throw new AssertionError("Empty/failed call generated a summary request");
    ConversationRepository.empty = false;
    ConversationSummarizer.run(c, "test", 0);
    if (ConversationRepository.saves != 1) throw new AssertionError("normal result");
    if (!"default".equals(Http.lastTier)) throw new AssertionError("Summary standard tier missing");
    ModelSettings.tier = "priority";
    Http.during = () -> ConversationRepository.revision++;
    ConversationSummarizer.run(c, "test", 0);
    if (!"priority".equals(Http.lastTier)) throw new AssertionError("Summary fast tier missing");
    if (ConversationRepository.saves != 1 || ConversationRepository.captured != 1)
      throw new AssertionError("stale revision");
    Http.during =
        () -> {
          ConversationSummarizer.invalidate();
          AccountStore.loggedIn = false;
        };
    ConversationSummarizer.run(c, "test", 0);
    if (ConversationRepository.saves != 1) throw new AssertionError("logout result");
    int requests = Http.requests;
    ConversationRepository.deleted = true;
    ConversationSummarizer.run(c, "test", 1);
    if (Http.requests != requests) throw new AssertionError("deleted conversation queried");
    System.out.println(
        "Conversation summaries: isolated requests, stale revision and logout cancellation passed");
  }
}
