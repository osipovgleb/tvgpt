package local.tvgpt.assistant;

/** TCL sometimes hides and immediately re-shows an existing session for one assist key. */
final class AssistantInvocation {
  private long hiddenAt = -10000;

  void hidden(long now) {
    hiddenAt = now;
  }

  boolean ignoreRestart(String source, long now) {
    if (source.isEmpty() && now >= hiddenAt && now - hiddenAt < 600) {
      hiddenAt = -10000;
      return true;
    }
    return false;
  }
}
